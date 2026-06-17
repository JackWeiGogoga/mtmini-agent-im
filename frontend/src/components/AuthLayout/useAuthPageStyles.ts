import { createStyles } from "antd-style";

const useStyles = createStyles(({ token }) => ({
  container: {
    minHeight: "100vh",
    display: "flex",
    justifyContent: "center",
    alignItems: "center",
    padding: "24px 16px",
    background: `linear-gradient(135deg, ${token.colorPrimaryBg} 0%, ${token.colorBgContainer} 100%)`,
    "@media (max-width: 991px)": {
      alignItems: "stretch",
    },
  },
  card: {
    width: "min(100%, var(--auth-card-width, 460px))",
    maxWidth: "100%",
    padding: "32px 24px",
    borderRadius: 16,
    boxShadow: "0 8px 32px rgba(0, 0, 0, 0.1)",
    "@media (max-width: 991px)": {
      padding: "24px 16px",
      borderRadius: 12,
    },
  },
  logo: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    marginBottom: 24,
  },
  logoImage: {
    width: 56,
    height: 56,
    marginBottom: 12,
  },
  title: {
    margin: 0,
    fontSize: 22,
    fontWeight: 600,
    color: token.colorTextHeading,
  },
  subtitle: {
    marginTop: 6,
    color: token.colorTextSecondary,
    fontSize: 14,
    textAlign: "center",
  },
  form: {
    marginTop: 16,
  },
  primaryButton: {
    width: "100%",
    height: 44,
    fontSize: 15,
    borderRadius: 8,
  },
  footer: {
    marginTop: 20,
    textAlign: "center",
    color: token.colorTextTertiary,
    fontSize: 12,
  },
  linkRow: {
    textAlign: "center",
    marginTop: 16,
  },
  formFooter: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 16,
    gap: 12,
    "@media (max-width: 991px)": {
      flexDirection: "column",
      alignItems: "flex-start",
    },
  },
  codeRow: {
    display: "flex",
    gap: 8,
    marginBottom: 24,
    alignItems: "flex-start",
    "@media (max-width: 991px)": {
      flexDirection: "column",
      marginBottom: 16,
    },
  },
  codeInput: {
    flex: 1,
    marginBottom: 0,
    "@media (max-width: 991px)": {
      width: "100%",
    },
  },
  codeButton: {
    width: 132,
    height: 40,
    "@media (max-width: 991px)": {
      width: "100%",
    },
  },
}));

export default useStyles;
