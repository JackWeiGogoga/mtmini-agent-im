import { useEffect, useState } from "react";

const LG_BREAKPOINT = 992;
const MD_BREAKPOINT = 768;

function getResponsiveState() {
  if (typeof window === "undefined") {
    return {
      isCompact: false,
      isMobile: false,
    };
  }

  return {
    isCompact: window.innerWidth < LG_BREAKPOINT,
    isMobile: window.innerWidth < MD_BREAKPOINT,
  };
}

export interface ResponsiveState {
  isCompact: boolean;
  isMobile: boolean;
}

export function useResponsive(): ResponsiveState {
  const [responsiveState, setResponsiveState] = useState<ResponsiveState>(
    getResponsiveState,
  );

  useEffect(() => {
    const handleResize = () => {
      setResponsiveState((current) => {
        const next = getResponsiveState();

        if (
          current.isCompact === next.isCompact &&
          current.isMobile === next.isMobile
        ) {
          return current;
        }

        return next;
      });
    };

    handleResize();
    window.addEventListener("resize", handleResize);

    return () => {
      window.removeEventListener("resize", handleResize);
    };
  }, []);

  return responsiveState;
}
