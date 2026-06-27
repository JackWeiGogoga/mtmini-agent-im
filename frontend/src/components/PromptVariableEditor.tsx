import type { ClipboardEvent, KeyboardEvent, MouseEvent } from "react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Button, Space, Tooltip } from "antd";
import { RedoOutlined, UndoOutlined } from "@ant-design/icons";

export interface PromptVariableOption {
  name: string;
  description?: string;
}

interface PromptVariableEditorProps {
  value?: string;
  onChange?: (value: string) => void;
  variables: PromptVariableOption[];
  minHeight?: number;
}

interface SuggestionState {
  start: number;
  end: number;
  keyword: string;
  left: number;
  top: number;
}

interface HistoryState {
  past: string[];
  future: string[];
}

const VARIABLE_REGEXP = /{{\s*([A-Za-z0-9_]+)\s*}}/g;
const VARIABLE_TRIGGER_REGEXP = /(?:{{|｛｛|「「|『『|「『)([A-Za-z0-9_\u4e00-\u9fa5]*)$/;
const CARET_ANCHOR = "\u200B";
const HISTORY_LIMIT = 100;

function variableTokenLength(name: string) {
  return name.length + 4;
}

function stripCaretAnchors(text: string) {
  return text.replaceAll(CARET_ANCHOR, "");
}

function getRawTextOffset(text: string, domOffset: number) {
  return stripCaretAnchors(text.slice(0, domOffset)).length;
}

function getDomTextOffset(text: string, rawOffset: number) {
  if (rawOffset >= stripCaretAnchors(text).length) return text.length;

  let rawCount = 0;
  for (let index = 0; index < text.length; index += 1) {
    if (text[index] === CARET_ANCHOR) continue;
    if (rawCount === rawOffset) return index;
    rawCount += 1;
  }
  return text.length;
}

function getNodeRawLength(node: Node): number {
  if (node.nodeType === Node.TEXT_NODE) return stripCaretAnchors(node.textContent || "").length;
  if (node.nodeType !== Node.ELEMENT_NODE) return 0;

  const element = node as HTMLElement;
  const variableName = element.dataset.variableName;
  if (variableName) return variableTokenLength(variableName);
  if (element.tagName === "BR") return 1;
  return Array.from(element.childNodes).reduce((sum, child) => sum + getNodeRawLength(child), 0);
}

function serializeEditor(root: HTMLElement | null): string {
  if (!root) return "";
  let result = "";

  const walk = (node: Node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      result += stripCaretAnchors(node.textContent || "");
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;

    const element = node as HTMLElement;
    const variableName = element.dataset.variableName;
    if (variableName) {
      result += `{{${variableName}}}`;
      return;
    }
    if (element.tagName === "BR") {
      result += "\n";
      return;
    }
    Array.from(element.childNodes).forEach(walk);
  };

  Array.from(root.childNodes).forEach(walk);
  return result;
}

function getCaretOffset(root: HTMLElement | null): number {
  if (!root) return 0;
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0) return 0;

  const range = selection.getRangeAt(0);
  let offset = 0;
  let found = false;

  const walk = (node: Node) => {
    if (found) return;
    if (node === range.startContainer) {
      if (node.nodeType === Node.TEXT_NODE) {
        offset += getRawTextOffset(node.textContent || "", range.startOffset);
      } else {
        const element = node as HTMLElement;
        Array.from(element.childNodes)
          .slice(0, range.startOffset)
          .forEach((child) => {
            offset += getNodeRawLength(child);
          });
      }
      found = true;
      return;
    }
    if (node.nodeType === Node.TEXT_NODE) {
      offset += stripCaretAnchors(node.textContent || "").length;
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;

    const element = node as HTMLElement;
    const variableName = element.dataset.variableName;
    if (variableName) {
      offset += variableTokenLength(variableName);
      return;
    }
    if (element.tagName === "BR") {
      offset += 1;
      return;
    }
    Array.from(element.childNodes).forEach(walk);
  };

  Array.from(root.childNodes).forEach(walk);
  return offset;
}

function setCaretOffset(root: HTMLElement | null, targetOffset: number) {
  if (!root) return;
  const selection = window.getSelection();
  if (!selection) return;

  const range = document.createRange();
  let current = 0;
  let placed = false;

  const placeBesideNode = (node: Node, after: boolean) => {
    const parent = node.parentNode || root;
    const index = Array.from(parent.childNodes).findIndex((child) => child === node);
    range.setStart(parent, Math.max(0, index + (after ? 1 : 0)));
    range.collapse(true);
    placed = true;
  };

  const walk = (node: Node) => {
    if (placed) return;
    if (node.nodeType === Node.TEXT_NODE) {
      const text = node.textContent || "";
      const length = stripCaretAnchors(text).length;
      if (targetOffset <= current + length) {
        range.setStart(node, getDomTextOffset(text, Math.max(0, targetOffset - current)));
        range.collapse(true);
        placed = true;
      } else {
        current += length;
      }
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;

    const element = node as HTMLElement;
    const variableName = element.dataset.variableName;
    if (variableName) {
      const length = variableTokenLength(variableName);
      if (targetOffset <= current) {
        placeBesideNode(node, false);
      } else if (targetOffset <= current + length) {
        placeBesideNode(node, true);
      } else {
        current += length;
      }
      return;
    }
    if (element.tagName === "BR") {
      if (targetOffset <= current + 1) {
        placeBesideNode(node, true);
      } else {
        current += 1;
      }
      return;
    }
    Array.from(element.childNodes).forEach(walk);
  };

  Array.from(root.childNodes).forEach(walk);
  if (!placed) {
    range.selectNodeContents(root);
    range.collapse(false);
  }
  selection.removeAllRanges();
  selection.addRange(range);
}

function insertTextAtSelection(root: HTMLElement | null, text: string) {
  if (!root) return undefined;
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0) return undefined;
  const range = selection.getRangeAt(0);
  if (!root.contains(range.commonAncestorContainer)) return undefined;

  const caretOffset = getCaretOffset(root);
  range.deleteContents();
  const textNode = document.createTextNode(text);
  range.insertNode(textNode);
  range.setStart(textNode, text.length);
  range.collapse(true);
  selection.removeAllRanges();
  selection.addRange(range);
  return caretOffset + text.length;
}

function getNodeStartOffset(root: HTMLElement | null, targetNode: Node): number {
  if (!root) return 0;
  let offset = 0;
  let found = false;

  const walk = (node: Node) => {
    if (found) return;
    if (node === targetNode) {
      found = true;
      return;
    }
    offset += getNodeRawLength(node);
  };

  Array.from(root.childNodes).forEach(walk);
  return offset;
}

function createVariableToken(name: string, variableMap: Map<string, PromptVariableOption>) {
  const variable = variableMap.get(name);
  const token = document.createElement("span");
  token.contentEditable = "false";
  token.dataset.variableName = name;
  token.title = `{{${name}}}`;
  token.textContent = variable?.description || `未知变量：${name}`;
  token.style.display = "inline-flex";
  token.style.alignItems = "center";
  token.style.maxWidth = "280px";
  token.style.height = "22px";
  token.style.margin = "0 2px";
  token.style.padding = "0 7px";
  token.style.border = `1px solid ${variable ? "#91caff" : "#ffa39e"}`;
  token.style.borderRadius = "4px";
  token.style.background = variable ? "#e6f4ff" : "#fff1f0";
  token.style.color = variable ? "#0958d9" : "#cf1322";
  token.style.fontSize = "12px";
  token.style.lineHeight = "20px";
  token.style.verticalAlign = "baseline";
  token.style.overflow = "hidden";
  token.style.textOverflow = "ellipsis";
  token.style.whiteSpace = "nowrap";
  token.style.cursor = "default";
  return token;
}

function renderTemplateValue(root: HTMLElement | null, value: string, variableMap: Map<string, PromptVariableOption>) {
  if (!root) return;
  root.replaceChildren();

  if (!value) {
    root.appendChild(document.createElement("br"));
    return;
  }

  let lastIndex = 0;
  let match: RegExpExecArray | null;
  VARIABLE_REGEXP.lastIndex = 0;
  while ((match = VARIABLE_REGEXP.exec(value))) {
    if (match.index > lastIndex) {
      root.appendChild(document.createTextNode(value.slice(lastIndex, match.index)));
    }
    root.appendChild(createVariableToken(match[1], variableMap));
    root.appendChild(document.createTextNode(CARET_ANCHOR));
    lastIndex = match.index + match[0].length;
  }

  if (lastIndex < value.length) {
    root.appendChild(document.createTextNode(value.slice(lastIndex)));
  }
  if (value.endsWith("\n")) {
    root.appendChild(document.createTextNode(CARET_ANCHOR));
  }
}

function getSuggestionPosition(root: HTMLElement | null): Pick<SuggestionState, "left" | "top"> {
  if (!root) return { left: 12, top: 36 };
  const rootRect = root.getBoundingClientRect();
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0) return { left: 12, top: 36 };

  const range = selection.getRangeAt(0).cloneRange();
  const rect = range.getClientRects()[0] || range.getBoundingClientRect();
  if (rect.width === 0 && rect.height === 0) return { left: 12, top: 36 };

  return {
    left: Math.max(8, Math.min(rect.left - rootRect.left, rootRect.width - 340)),
    top: Math.max(34, rect.bottom - rootRect.top + 6),
  };
}

function renderAndRestoreCaret(
  root: HTMLElement | null,
  value: string,
  variableMap: Map<string, PromptVariableOption>,
  caretOffset?: number,
) {
  renderTemplateValue(root, value, variableMap);
  if (caretOffset === undefined) return;
  window.requestAnimationFrame(() => setCaretOffset(root, caretOffset));
}

function matchesSuggestionKeyword(variable: PromptVariableOption, keyword: string) {
  if (!keyword) return true;
  const normalizedKeyword = keyword.toLowerCase();
  return variable.name.toLowerCase().includes(normalizedKeyword) || (variable.description || "").includes(keyword);
}

function resolveSuggestionRange(value: string, fallback: SuggestionState) {
  const prefix = value.slice(0, fallback.end);
  const match = prefix.match(VARIABLE_TRIGGER_REGEXP);
  if (!match || match.index === undefined) {
    return { start: fallback.start, end: fallback.end };
  }
  return { start: match.index, end: fallback.end };
}

export default function PromptVariableEditor({
  value = "",
  onChange,
  variables,
  minHeight = 360,
}: PromptVariableEditorProps) {
  const editorRef = useRef<HTMLDivElement | null>(null);
  const valueRef = useRef(value);
  const historyRef = useRef<HistoryState>({ past: [], future: [] });
  const composingRef = useRef(false);
  const suggestionKeywordRef = useRef("");
  const suggestionItemRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const variableMap = useMemo(() => new Map(variables.map((item) => [item.name, item])), [variables]);
  const [editorValue, setEditorValue] = useState(value);
  const [suggestion, setSuggestion] = useState<SuggestionState | null>(null);
  const [activeSuggestionIndex, setActiveSuggestionIndex] = useState(0);
  const [historyStatus, setHistoryStatus] = useState({ canUndo: false, canRedo: false });
  const suggestions = useMemo(() => {
    if (!suggestion) return [];
    return variables.filter((item) => matchesSuggestionKeyword(item, suggestion.keyword)).slice(0, 8);
  }, [suggestion, variables]);
  const selectedSuggestionIndex = suggestions.length === 0 ? 0 : Math.min(activeSuggestionIndex, suggestions.length - 1);

  const syncHistoryStatus = () => {
    setHistoryStatus({
      canUndo: historyRef.current.past.length > 0,
      canRedo: historyRef.current.future.length > 0,
    });
  };

  useEffect(() => {
    suggestionItemRefs.current = suggestionItemRefs.current.slice(0, suggestions.length);
  }, [suggestions.length]);

  useEffect(() => {
    if (suggestions.length === 0) return;
    suggestionItemRefs.current[selectedSuggestionIndex]?.scrollIntoView({
      block: "nearest",
    });
  }, [selectedSuggestionIndex, suggestions.length]);

  const applyValue = useCallback(
    (nextValue: string, caretOffset?: number, pushHistory = true) => {
      const previous = valueRef.current;
      if (nextValue === previous) {
        if (caretOffset !== undefined) setCaretOffset(editorRef.current, caretOffset);
        return;
      }

      if (pushHistory) {
        historyRef.current.past = [...historyRef.current.past.slice(-HISTORY_LIMIT + 1), previous];
        historyRef.current.future = [];
        syncHistoryStatus();
      }

      valueRef.current = nextValue;
      setEditorValue(nextValue);
      onChange?.(nextValue);
      renderAndRestoreCaret(editorRef.current, nextValue, variableMap, caretOffset);
    },
    [onChange, variableMap],
  );

  const updateSuggestion = useCallback(() => {
    if (composingRef.current) {
      suggestionKeywordRef.current = "";
      setActiveSuggestionIndex(0);
      setSuggestion(null);
      return;
    }
    const root = editorRef.current;
    if (!root || variables.length === 0) {
      suggestionKeywordRef.current = "";
      setActiveSuggestionIndex(0);
      setSuggestion(null);
      return;
    }
    const cursor = getCaretOffset(root);
    const currentValue = serializeEditor(root);
    const prefix = currentValue.slice(0, cursor);
    const match = prefix.match(VARIABLE_TRIGGER_REGEXP);
    if (!match) {
      suggestionKeywordRef.current = "";
      setActiveSuggestionIndex(0);
      setSuggestion(null);
      return;
    }
    if (suggestionKeywordRef.current !== match[1]) {
      suggestionKeywordRef.current = match[1];
      setActiveSuggestionIndex(0);
    }
    const position = getSuggestionPosition(root);
    setSuggestion({
      start: cursor - match[0].length,
      end: cursor,
      keyword: match[1],
      ...position,
    });
  }, [variables.length]);

  useEffect(() => {
    if (value === valueRef.current) return;
    valueRef.current = value;
    historyRef.current = { past: [], future: [] };
    renderAndRestoreCaret(editorRef.current, value, variableMap, getCaretOffset(editorRef.current));
    const frame = window.requestAnimationFrame(() => {
      setEditorValue(value);
      syncHistoryStatus();
    });
    return () => window.cancelAnimationFrame(frame);
  }, [value, variableMap]);

  useEffect(() => {
    renderAndRestoreCaret(editorRef.current, valueRef.current, variableMap, getCaretOffset(editorRef.current));
  }, [variableMap]);

  const commitFromDom = useCallback(() => {
    if (composingRef.current) return;
    const root = editorRef.current;
    const nextValue = serializeEditor(root);
    const caretOffset = getCaretOffset(root);
    applyValue(nextValue, caretOffset);
    window.requestAnimationFrame(updateSuggestion);
  }, [applyValue, updateSuggestion]);

  const insertVariable = useCallback(
    (name: string) => {
      if (!suggestion) return;
      const currentValue = serializeEditor(editorRef.current) || valueRef.current;
      const token = `{{${name}}}`;
      const range = resolveSuggestionRange(currentValue, suggestion);
      const nextValue = `${currentValue.slice(0, range.start)}${token}${currentValue.slice(range.end)}`;
      setSuggestion(null);
      applyValue(nextValue, range.start + token.length);
      window.requestAnimationFrame(() => editorRef.current?.focus());
    },
    [applyValue, suggestion],
  );

  const undo = useCallback(() => {
    const previous = historyRef.current.past.pop();
    if (previous === undefined) return;
    historyRef.current.future.unshift(valueRef.current);
    syncHistoryStatus();
    setSuggestion(null);
    valueRef.current = previous;
    setEditorValue(previous);
    onChange?.(previous);
    renderAndRestoreCaret(editorRef.current, previous, variableMap, Math.min(previous.length, getCaretOffset(editorRef.current)));
  }, [onChange, variableMap]);

  const redo = useCallback(() => {
    const next = historyRef.current.future.shift();
    if (next === undefined) return;
    historyRef.current.past.push(valueRef.current);
    syncHistoryStatus();
    setSuggestion(null);
    valueRef.current = next;
    setEditorValue(next);
    onChange?.(next);
    renderAndRestoreCaret(editorRef.current, next, variableMap, Math.min(next.length, getCaretOffset(editorRef.current)));
  }, [onChange, variableMap]);

  const removeVariableNearCaret = (direction: "backward" | "forward") => {
    const cursor = getCaretOffset(editorRef.current);
    const currentValue = serializeEditor(editorRef.current);
    let match: RegExpExecArray | null;
    VARIABLE_REGEXP.lastIndex = 0;
    while ((match = VARIABLE_REGEXP.exec(currentValue))) {
      const start = match.index;
      const end = match.index + match[0].length;
      const shouldRemove = direction === "backward" ? end === cursor : start === cursor;
      if (!shouldRemove) continue;
      applyValue(`${currentValue.slice(0, start)}${currentValue.slice(end)}`, start);
      setSuggestion(null);
      return true;
    }
    return false;
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.nativeEvent.isComposing || composingRef.current) {
      return;
    }
    const isMeta = event.metaKey || event.ctrlKey;
    const key = event.key.toLowerCase();
    if (isMeta && key === "z" && event.shiftKey) {
      event.preventDefault();
      redo();
      return;
    }
    if (isMeta && key === "z") {
      event.preventDefault();
      undo();
      return;
    }
    if (isMeta && key === "y") {
      event.preventDefault();
      redo();
      return;
    }
    if (suggestions.length > 0 && event.key === "ArrowDown") {
      event.preventDefault();
      setActiveSuggestionIndex((current) => (current + 1) % suggestions.length);
      return;
    }
    if (suggestions.length > 0 && event.key === "ArrowUp") {
      event.preventDefault();
      setActiveSuggestionIndex((current) => (current - 1 + suggestions.length) % suggestions.length);
      return;
    }
    if ((event.key === "Enter" || event.key === "Tab") && suggestions.length > 0) {
      event.preventDefault();
      insertVariable(suggestions[selectedSuggestionIndex]?.name || suggestions[0].name);
      return;
    }
    if (event.key === "Escape") {
      setSuggestion(null);
      return;
    }
    if (event.key === "Enter") {
      event.preventDefault();
      const caretOffset = insertTextAtSelection(editorRef.current, "\n");
      const nextValue = serializeEditor(editorRef.current);
      applyValue(nextValue, caretOffset);
      return;
    }
    if (event.key === "Backspace" && removeVariableNearCaret("backward")) {
      event.preventDefault();
      return;
    }
    if (event.key === "Delete" && removeVariableNearCaret("forward")) {
      event.preventDefault();
    }
  };

  const handlePaste = (event: ClipboardEvent<HTMLDivElement>) => {
    event.preventDefault();
    const caretOffset = insertTextAtSelection(editorRef.current, event.clipboardData.getData("text/plain"));
    const nextValue = serializeEditor(editorRef.current);
    applyValue(nextValue, caretOffset);
    window.requestAnimationFrame(updateSuggestion);
  };

  const handleCompositionStart = () => {
    composingRef.current = true;
    setSuggestion(null);
  };

  const handleCompositionEnd = () => {
    composingRef.current = false;
    window.requestAnimationFrame(() => {
      commitFromDom();
      updateSuggestion();
    });
  };

  const handleTokenClick = (event: MouseEvent<HTMLElement>) => {
    const name = event.currentTarget.dataset.variableName;
    if (!name) return;
    const offset = getNodeStartOffset(editorRef.current, event.currentTarget) + variableTokenLength(name);
    window.requestAnimationFrame(() => setCaretOffset(editorRef.current, offset));
  };

  useEffect(() => {
    const root = editorRef.current;
    if (!root) return undefined;
    const handleClick = (event: globalThis.MouseEvent) => {
      const target = event.target as HTMLElement | null;
      const token = target?.closest<HTMLElement>("[data-variable-name]");
      if (!token || !root.contains(token)) return;
      handleTokenClick({ currentTarget: token } as MouseEvent<HTMLElement>);
    };
    root.addEventListener("click", handleClick);
    return () => root.removeEventListener("click", handleClick);
  }, []);

  return (
    <div style={{ position: "relative" }}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 8 }}>
        <Space size={6}>
          <Tooltip title="撤销">
            <Button size="small" icon={<UndoOutlined />} disabled={!historyStatus.canUndo} onClick={undo} />
          </Tooltip>
          <Tooltip title="重做">
            <Button size="small" icon={<RedoOutlined />} disabled={!historyStatus.canRedo} onClick={redo} />
          </Tooltip>
        </Space>
        <span style={{ color: "#8c8c8c" }}>{editorValue.length} 字符</span>
      </div>

      <div style={{ position: "relative" }}>
        <div
          ref={editorRef}
          contentEditable
          suppressContentEditableWarning
          role="textbox"
          aria-multiline="true"
          tabIndex={0}
          onInput={commitFromDom}
          onKeyDown={handleKeyDown}
          onKeyUp={() => window.requestAnimationFrame(updateSuggestion)}
          onMouseUp={() => window.requestAnimationFrame(updateSuggestion)}
          onCompositionStart={handleCompositionStart}
          onCompositionEnd={handleCompositionEnd}
          onPaste={handlePaste}
          spellCheck={false}
          style={{
            boxSizing: "border-box",
            width: "100%",
            minHeight,
            padding: "10px 12px",
            border: "1px solid #d9d9d9",
            borderRadius: 6,
            background: "#fff",
            color: "#1f1f1f",
            fontFamily:
              'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
            fontSize: 13,
            lineHeight: "24px",
            letterSpacing: 0,
            whiteSpace: "pre-wrap",
            overflow: "auto",
            wordBreak: "break-word",
            outline: "none",
            cursor: "text",
          }}
        />
        {!editorValue && (
          <div style={{ position: "absolute", top: 11, left: 13, color: "#bfbfbf", pointerEvents: "none" }}>
            输入提示词内容，输入 {"{{"}、｛｛、「「、『『 或 「『 可插入变量
          </div>
        )}
        {suggestions.length > 0 && (
          <div
            style={{
              position: "absolute",
              left: suggestion?.left ?? 12,
              top: suggestion?.top ?? 36,
              zIndex: 20,
              width: 340,
              maxHeight: 240,
              overflow: "auto",
              border: "1px solid #d9d9d9",
              borderRadius: 6,
              background: "#fff",
              boxShadow: "0 6px 16px rgba(0, 0, 0, 0.12)",
            }}
          >
            {suggestions.map((item, index) => {
              const active = index === selectedSuggestionIndex;
              return (
              <button
                key={item.name}
                ref={(element) => {
                  suggestionItemRefs.current[index] = element;
                }}
                type="button"
                onMouseEnter={() => setActiveSuggestionIndex(index)}
                onMouseDown={(event) => {
                  event.preventDefault();
                  insertVariable(item.name);
                }}
                style={{
                  display: "block",
                  width: "100%",
                  border: 0,
                  background: active ? "#e6f4ff" : "transparent",
                  padding: "9px 10px",
                  textAlign: "left",
                  cursor: "pointer",
                }}
              >
                <div style={{ color: "#0958d9", fontWeight: 500 }}>{item.description || item.name}</div>
                <code style={{ color: "#8c8c8c" }}>{`{{${item.name}}}`}</code>
              </button>
              );
            })}
          </div>
        )}
      </div>

    </div>
  );
}
