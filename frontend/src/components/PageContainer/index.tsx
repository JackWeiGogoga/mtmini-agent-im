/**
 * 通用页面容器组件
 * 提供统一的页面布局样式
 */
import React from "react";
import { createStyles } from "antd-style";

const useStyles = createStyles(() => ({
  container: {
    background: "#fff",
    borderRadius: 8,
    padding: 20,
    minHeight: "100%",
    "@media (max-width: 991px)": {
      padding: 12,
      borderRadius: 6,
    },
  },
  toolbar: {
    marginBottom: 16,
    display: "flex",
    justifyContent: "space-between",
    flexWrap: "wrap" as const,
    gap: 16,
    "@media (max-width: 991px)": {
      flexDirection: "column",
      alignItems: "stretch",
      gap: 12,
      "& > .ant-btn": {
        width: "auto",
        alignSelf: "flex-start",
      },
    },
  },
  searchForm: {
    display: "flex",
    gap: 16,
    flexWrap: "wrap" as const,
    "@media (max-width: 991px)": {
      width: "100%",
      display: "grid",
      gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
      gap: 12,
      alignItems: "start",
      "& > .ant-form-item, & > .ant-btn, & > .ant-space": {
        width: "100%",
        marginInlineEnd: 0,
        marginBottom: 0,
        minWidth: 0,
      },
      "& > .ant-form-item .ant-form-item-control, & > .ant-form-item .ant-form-item-control-input, & > .ant-form-item .ant-form-item-control-input-content": {
        width: "100%",
      },
      "& .ant-input, & .ant-input-affix-wrapper, & .ant-input-number, & .ant-picker, & .ant-select, & .ant-btn": {
        width: "100% !important",
      },
    },
  },
  compactFull: {
    "@media (max-width: 991px)": {
      gridColumn: "1 / -1",
      width: "100% !important",
    },
  },
  searchActions: {
    display: "flex",
    flexWrap: "wrap" as const,
    gap: 8,
    "@media (max-width: 991px)": {
      gridColumn: "1 / -1",
      width: "100%",
      "& .ant-btn": {
        flex: "1 1 0",
        minWidth: 0,
      },
    },
  },
  toolbarAction: {
    "@media (max-width: 991px)": {
      alignSelf: "flex-start",
    },
  },
}));

interface PageContainerProps {
  /** 页面标题（可选，仅用于语义化，不再显示） */
  title?: string;
  /** 子内容 */
  children: React.ReactNode;
  /** 额外的类名 */
  className?: string;
}

/**
 * 页面容器组件
 * 提供统一的白色背景容器和内边距
 */
const PageContainer: React.FC<PageContainerProps> = ({
  children,
  className,
}) => {
  const { styles, cx } = useStyles();

  return <div className={cx(styles.container, className)}>{children}</div>;
};

export { useStyles as usePageStyles };
export default PageContainer;
