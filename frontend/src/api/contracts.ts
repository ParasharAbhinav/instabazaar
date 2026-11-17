// Hand-maintained from the supplied contract; replace with OpenAPI generation when available.
export type Role = "BUYER" | "SELLER" | "ADMIN";
export interface User {
  id: string;
  name: string;
  email: string;
  role: Role;
  enabled: boolean;
  version: number;
  createdAt: string;
}
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface Category {
  id: string;
  name: string;
  slug: string;
  description: string | null;
}
export interface Rating {
  average: number | null;
  count: number;
}
export interface Store {
  id: string;
  storeName: string;
  description: string;
  logoUrl: string | null;
  publicEmail: string | null;
  publicPhone: string | null;
  whatsappNumber: string | null;
  instagramUrl: string | null;
  facebookUrl: string | null;
  rating: Rating;
  createdAt: string;
  version: number;
}
export interface ProductImage {
  id: string;
  assetId: string;
  url: string;
  displayOrder: number;
  altText: string;
}
export interface Asset {
  id: string;
  previewUrl: string;
  contentType: string;
  byteSize: number;
  width: number;
  height: number;
}
export interface ProductSummary {
  id: string;
  title: string;
  price: string;
  currency: "INR";
  available: boolean;
  thumbnailUrl: string | null;
  seller: Pick<Store, "id" | "storeName">;
  category: Category;
  rating: Rating;
}
export interface ProductDetail
  extends Omit<ProductSummary, "thumbnailUrl" | "seller"> {
  description: string;
  colour: string;
  sizes: string[];
  material: string | null;
  occasion: string | null;
  brand: string | null;
  stockQuantity: number;
  sourceUrl: string | null;
  seller: Store;
  images: ProductImage[];
  tags: string[];
  createdAt: string;
  updatedAt: string;
  version: number;
}
export type ListingStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";
export interface ManagedProduct {
  reelCaption?: string | null;
  product: ProductDetail;
  availabilityEnabled: boolean;
  listingStatus: ListingStatus;
  moderationStatus: "VISIBLE" | "HIDDEN";
}
export interface Tag {
  id: string;
  tag: string;
  source: "SELLER" | "SYSTEM";
  status: "SUGGESTED" | "APPROVED" | "REJECTED";
}
export interface TagSet {
  items: Tag[];
  productVersion: number;
}
export interface Review {
  id: string;
  productId: string;
  reviewer: { id: string; name: string };
  rating: number;
  text: string | null;
  status: "PENDING" | "APPROVED" | "REJECTED";
  version: number;
  createdAt: string;
  updatedAt: string;
}
export interface SavedProduct {
  productId: string;
  savedAt: string;
  visibility: "VISIBLE" | "UNAVAILABLE";
  product: ProductSummary | null;
}
export interface Report {
  id: string;
  targetType: "PRODUCT" | "REVIEW";
  targetId: string;
  reason: "MISLEADING" | "INAPPROPRIATE" | "SPAM" | "OTHER";
  details: string | null;
  status: "OPEN" | "RESOLVED" | "DISMISSED";
  resolutionNote: string | null;
  resolvedAt: string | null;
  version: number;
  createdAt: string;
}
export interface Statistics {
  enabledUsers: number;
  sellerStores: number;
  publicProducts: number;
  pendingReviews: number;
  openReports: number;
}
export interface ProductInput {
  reelCaption?: string | null;
  categoryId: string;
  title: string;
  description: string;
  price: string;
  currency: "INR";
  colour: string;
  sizes: string[];
  material: string | null;
  occasion: string | null;
  brand: string | null;
  stockQuantity: number;
  availabilityEnabled: boolean;
  sourceUrl: string | null;
  sellerTags: string[];
  images: { assetId: string; displayOrder: number; altText: string }[];
  version?: number;
}
export interface ApiErrorBody {
  code: string;
  message: string;
  fieldErrors: { field: string; message: string }[];
  traceId: string;
  timestamp: string;
}
