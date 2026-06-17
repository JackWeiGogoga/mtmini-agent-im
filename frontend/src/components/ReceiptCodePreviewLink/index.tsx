import React, { useState } from "react";
import { Image, Typography } from "antd";

interface ReceiptCodePreviewLinkProps {
  src: string;
  label?: string;
  thumbnailWidth?: number;
  useThumbnail?: boolean;
}

const ReceiptCodePreviewLink: React.FC<ReceiptCodePreviewLinkProps> = ({
  src,
  label = "查看",
  thumbnailWidth = 56,
  useThumbnail = false,
}) => {
  const [previewOpen, setPreviewOpen] = useState(false);

  return (
    <>
      {useThumbnail ? (
        <img
          alt="收款码缩略图"
          onClick={() => {
            setPreviewOpen(true);
          }}
          src={src}
          style={{
            border: "1px solid #f0f0f0",
            borderRadius: 8,
            cursor: "zoom-in",
            display: "block",
            height: thumbnailWidth,
            objectFit: "cover",
            width: thumbnailWidth,
          }}
        />
      ) : (
        <Typography.Link
          onClick={(event) => {
            event.preventDefault();
            setPreviewOpen(true);
          }}
        >
          {label}
        </Typography.Link>
      )}
      <div style={{ display: "none" }}>
        <Image
          alt="收款码"
          preview={{
            imageRender: (originalNode) => originalNode,
            onOpenChange: (visible) => {
              setPreviewOpen(visible);
            },
            open: previewOpen,
          }}
          src={src}
        />
      </div>
    </>
  );
};

export default ReceiptCodePreviewLink;
