/**
 * 应用入口组件
 */
import React from "react";
import { RouterProvider } from "react-router-dom";
import { ConfigProvider, App as AntdApp } from "antd";
import zhCN from "antd/locale/zh_CN";
import dayjs from "dayjs";
import "dayjs/locale/zh-cn";
import { AuthProvider } from "@/contexts/AuthContext";
import AppMessageContext from "@/components/AppMessageContext";
import themeTokens from "./theme/antd-themeConfig-light.json";
import router from "./router";

dayjs.locale("zh-cn");

const App: React.FC = () => {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          fontFamily: "PingFang SC,Heiti SC,Arial",
          ...themeTokens.token,
        },
        components: {
          ...themeTokens.components,
          // 统一 Input 组件高度，与 Select、Button 保持一致
          Input: {
            ...themeTokens.components?.Input,
            paddingBlock: 3, // 减少垂直内边距以匹配其他组件高度
          },
        },
      }}
    >
      <AntdApp>
        <AppMessageContext />
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </AntdApp>
    </ConfigProvider>
  );
};

export default App;
