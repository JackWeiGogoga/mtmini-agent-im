/**
 * Ant Design App 上下文组件
 * 提供 message、modal、notification 的静态方法访问
 */
import { useEffect } from "react";
import { App } from "antd";
import type { MessageInstance } from "antd/es/message/interface";
import type { ModalStaticFunctions } from "antd/es/modal/confirm";
import type { NotificationInstance } from "antd/es/notification/interface";

// 导出静态方法实例
let message: MessageInstance;
let modal: Omit<ModalStaticFunctions, "warn">;
let notification: NotificationInstance;

/**
 * App 上下文组件
 * 需要放在 AntdApp 组件内部使用
 */

const AppMessageContext = () => {
  const staticFunction = App.useApp();

  useEffect(() => {
    message = staticFunction.message;
    modal = staticFunction.modal;
    notification = staticFunction.notification;
  }, [staticFunction]);

  return null;
};

export { message, modal, notification };
export default AppMessageContext;
