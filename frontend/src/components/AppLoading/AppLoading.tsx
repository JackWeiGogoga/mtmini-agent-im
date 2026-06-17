import React from "react";
import { Spin, Typography } from "antd";
import { LoadingOutlined } from "@ant-design/icons";
import { createStyles } from "antd-style";

const { Text } = Typography;

const useStyles = createStyles(({ token }) => ({
  fullPage: {
    position: "fixed",
    inset: 0,
    zIndex: 1000,
    minHeight: "100vh",
  },
  block: {
    minHeight: 200,
  },
  wrapper: {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    width: "100%",
    background: "transparent",
  },
  card: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    gap: 12,
    padding: "20px 24px",
    borderRadius: 16,
    background: token.colorBgContainer,
    boxShadow: "0 10px 30px rgba(15, 23, 42, 0.06)",
  },
  indicator: {
    fontSize: 22,
    color: token.colorPrimary,
  },
  text: {
    fontSize: 13,
    color: token.colorTextSecondary,
  },
}));

interface AppLoadingProps {
  text?: string;
  fullPage?: boolean;
}

const AppLoading: React.FC<AppLoadingProps> = ({
  text = "加载中...",
  fullPage = false,
}) => {
  const { styles, cx } = useStyles();

  return (
    <div className={cx(styles.wrapper, fullPage ? styles.fullPage : styles.block)}>
      <div className={styles.card}>
        <Spin indicator={<LoadingOutlined spin className={styles.indicator} />} />
        <Text className={styles.text}>{text}</Text>
      </div>
    </div>
  );
};

export default AppLoading;
