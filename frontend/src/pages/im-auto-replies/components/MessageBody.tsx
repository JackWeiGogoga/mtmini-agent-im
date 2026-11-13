import { useState } from "react";
import { Image, Typography } from "antd";

const { Paragraph, Link } = Typography;

/** 与后端 ImMessageTypeEnum 对齐 */
const MSG_TYPE_IMAGE = 2;

/**
 * 图片消息的正文是美团图床 URL，直接当文本渲染就是一长串链接，看不出顾客发了什么。
 * 只认图片类型：OTHER 里可能是语音、视频等无法用 img 呈现的内容，仍按原文展示。
 */
export function isImageMessage(msgType?: number, content?: string): boolean {
  return msgType === MSG_TYPE_IMAGE && /^https?:\/\//i.test(content?.trim() || "");
}

interface MessageBodyProps {
  msgType?: number;
  content?: string;
  /** 正文为空时的占位文案 */
  emptyText?: string;
  /** 文本折叠行数；不传则完整展示 */
  ellipsisRows?: number;
  maxImageWidth?: number;
}

/**
 * 会话正文渲染：图片消息出图，其余按纯文本。
 */
export function MessageBody({
  msgType,
  content,
  emptyText = "-",
  ellipsisRows,
  maxImageWidth = 160,
}: MessageBodyProps) {
  const [imageBroken, setImageBroken] = useState(false);
  const url = content?.trim() || "";

  if (isImageMessage(msgType, content)) {
    // 图床链接可能过期或被防盗链拦下，此时退回可点开的原始链接，不让气泡空着
    return imageBroken
      ? <Link href={url} target="_blank" rel="noreferrer">[图片] 点击查看原图</Link>
      : <Image
          src={url}
          alt="顾客发送的图片"
          style={{ maxWidth: maxImageWidth, borderRadius: 6 }}
          onError={() => setImageBroken(true)}
        />;
  }

  const text = content || emptyText;
  return ellipsisRows
    ? <Paragraph
        ellipsis={{ rows: ellipsisRows, expandable: true, symbol: "展开" }}
        style={{ margin: 0, whiteSpace: "pre-wrap" }}
      >{text}</Paragraph>
    : <Paragraph
        style={{ margin: 0, whiteSpace: "pre-wrap", wordBreak: "break-word" }}
      >{text}</Paragraph>;
}
