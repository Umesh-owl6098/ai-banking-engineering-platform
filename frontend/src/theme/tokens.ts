export const layout = {
  sidebarWidth: 240,
  pageMaxWidth: 1440,
  pagePadding: { xs: 2, md: 2.5 },
  sectionGap: 2,
} as const;

export const surfaces = {
  cardRadius: 2,
  cardBorder: "1px solid",
  cardBorderColor: "divider",
  cardShadow: "0 1px 2px rgba(15, 23, 42, 0.06)",
} as const;

export const table = {
  stickyHeader: true,
  size: "small" as const,
  containerMaxHeight: 520,
} as const;
