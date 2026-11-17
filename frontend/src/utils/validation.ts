export function safeSocial(
  value: string | null,
  platform?: "instagram" | "facebook",
): string | null {
  if (!value) return null;
  try {
    const u = new URL(value);
    const hosts = platform
      ? [platform + ".com", "www." + platform + ".com"]
      : [
          "instagram.com",
          "www.instagram.com",
          "facebook.com",
          "www.facebook.com",
        ];
    return u.protocol === "https:" &&
      !u.username &&
      !u.password &&
      hosts.includes(u.hostname)
      ? u.href
      : null;
  } catch {
    return null;
  }
}
export function safeImage(value: string | null | undefined) {
  if (!value) return "/images/product-placeholder.svg";
  try {
    const u = new URL(value, location.origin);
    return u.protocol === "https:" ||
      (u.origin === location.origin && u.protocol === location.protocol)
      ? u.href
      : "/images/product-placeholder.svg";
  } catch {
    return "/images/product-placeholder.svg";
  }
}
export const money = (price: string) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(Number(price));
export const nullable = (v: FormDataEntryValue | null) =>
  typeof v === "string" && v.trim() ? v.trim() : null;
export const uniqueList = (v: string, upper = false) => [
  ...new Set(
    v
      .split(",")
      .map((s) => (upper ? s.trim().toUpperCase() : s.trim().toLowerCase()))
      .filter(Boolean),
  ),
];
export function validatePrice(price: string) {
  return /^(0|[1-9]\d{0,9})(\.\d{1,2})?$/.test(price);
}
export function returnPath(value: string | null) {
  return value?.startsWith("/") &&
    !value.startsWith("//") &&
    !value.includes("\\")
    ? value
    : "/";
}

// --- Domain Contract & Formatting Helpers ---
export function formatInr(amount: string | number): string {
  const num = typeof amount === 'string' ? Number.parseFloat(amount) : amount;
  if (Number.isNaN(num)) return '₹0.00';
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
  }).format(num);
}

export function isValidE164Phone(phone: string): boolean {
  return /^\+[1-9]\d{7,14}$/.test(phone.trim());
}

export function toWhatsAppUrl(phone: string, productTitle: string): string {
  const digits = phone.replace(/[^\d]/g, '');
  const text = encodeURIComponent('Hi, I am interested in buying "' + productTitle + '" on InstaBazaar.');
  return 'https://wa.me/' + digits + '?text=' + text;
}

export function isValidPassword(password: string): boolean {
  return typeof password === 'string' && password.trim().length >= 8;
}

export function normalizeSearchParams(params: {
  q?: string;
  minPrice?: string;
  maxPrice?: string;
  page?: number;
}) {
  return {
    q: params.q ? params.q.trim() : undefined,
    minPrice: params.minPrice ? params.minPrice.trim() : undefined,
    maxPrice: params.maxPrice ? params.maxPrice.trim() : undefined,
    page: params.page ?? 0,
  };
}
