import React from "react";
import { Typography } from "antd";

const { Title } = Typography;

const Welcome: React.FC = () => {
  return <Title level={2}>Hello World</Title>;
};

export default Welcome;
