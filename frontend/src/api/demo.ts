import type { Category, ProductDetail, ProductSummary } from './contracts';

export const IS_DEMO_ENABLED = false;
export const DEMO_PRODUCTS: ProductSummary[] = [];

export const categories: Category[] = [];

export const products: (ProductDetail & {
  images: Array<{ id?: number | string; url: string; altText?: string; sortOrder?: number }>;
  sizes: Array<{ id?: number | string; label: string; sizeLabel?: string; stock?: number; quantity?: number; available?: boolean }>;
   [key: string]: any;
})[] = [];

export const stores: any[] = [];

export function demoSearch(_params?: Record<string, any>): {
  items: ProductSummary[];
  content: ProductSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
} {
  return {
    items: [],
    content: [],
    page: 0,
    size: 12,
    totalElements: 0,
    totalPages: 0,
  };
}