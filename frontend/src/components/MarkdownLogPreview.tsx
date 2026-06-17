import React, { Fragment, useMemo, useState } from "react";
import { Button, Space, Typography } from "antd";

const { Paragraph, Text } = Typography;

type MarkdownLogPreviewProps = {
  markdown?: string;
  fallbackText?: string;
};

function stripMarkdown(markdown: string) {
  return markdown
    .replace(/\*\*(.*?)\*\*/g, "$1")
    .replace(/^[-*]\s+/gm, "")
    .replace(/\n+/g, " ")
    .trim();
}

function renderInline(text: string): React.ReactNode[] {
  const nodes: React.ReactNode[] = [];
  const pattern = /\*\*(.+?)\*\*/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = pattern.exec(text)) != null) {
    if (match.index > lastIndex) {
      nodes.push(text.slice(lastIndex, match.index));
    }
    nodes.push(<strong key={`${match.index}-${match[0]}`}>{match[1]}</strong>);
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex));
  }
  return nodes;
}

function renderMarkdown(markdown: string) {
  const lines = markdown
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
  const nodes: React.ReactNode[] = [];
  let listItems: string[] = [];

  const flushList = () => {
    if (!listItems.length) {
      return;
    }
    nodes.push(
      <ul
        key={`list-${nodes.length}`}
        style={{ margin: "0 0 8px 18px", padding: 0 }}
      >
        {listItems.map((item, index) => (
          <li key={`${item}-${index}`} style={{ marginBottom: 4 }}>
            {renderInline(item)}
          </li>
        ))}
      </ul>,
    );
    listItems = [];
  };

  lines.forEach((line) => {
    if (line.startsWith("- ")) {
      listItems.push(line.slice(2).trim());
      return;
    }
    flushList();
    nodes.push(
      <Paragraph key={`${line}-${nodes.length}`} style={{ marginBottom: 8 }}>
        {renderInline(line)}
      </Paragraph>,
    );
  });
  flushList();
  return nodes;
}

const MarkdownLogPreview: React.FC<MarkdownLogPreviewProps> = ({
  markdown,
  fallbackText,
}) => {
  const [expanded, setExpanded] = useState(false);
  const content = useMemo(
    () =>
      markdown && markdown.trim()
        ? markdown.trim()
        : fallbackText?.trim() || "",
    [fallbackText, markdown],
  );
  const previewText = useMemo(() => stripMarkdown(content), [content]);

  if (!content) {
    return <Text type="secondary">-</Text>;
  }

  return (
    <Space orientation="vertical" size={8} style={{ width: "100%" }}>
      {expanded ? (
        <div>
          {renderMarkdown(content).map((node, index) => (
            <Fragment key={index}>{node}</Fragment>
          ))}
        </div>
      ) : (
        <Paragraph
          ellipsis={{ rows: 3 }}
          style={{ marginBottom: 0, whiteSpace: "pre-wrap" }}
        >
          {previewText}
        </Paragraph>
      )}
      <Button
        type="link"
        size="small"
        style={{ padding: 0, alignSelf: "flex-start" }}
        onClick={() => setExpanded((value) => !value)}
      >
        {expanded ? "收起详情" : "展开详情"}
      </Button>
    </Space>
  );
};

export default MarkdownLogPreview;
