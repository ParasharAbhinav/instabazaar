import { describe, expect, it } from "vitest";
import {
  returnPath,
  safeSocial,
  uniqueList,
  validatePrice,
} from "../utils/validation";
describe("external destinations", () => {
  it("allows only approved social hosts", () => {
    expect(safeSocial("https://www.instagram.com/store/")).toBe(
      "https://www.instagram.com/store/",
    );
    for (const u of [
      "javascript:alert(1)",
      "https://instagram.com.evil.test",
      "http://instagram.com",
      "https://user:pass@instagram.com",
      "https://evil.test",
    ])
      expect(safeSocial(u)).toBeNull();
    expect(safeSocial("https://facebook.com/store", "instagram")).toBeNull();
  });
  it("rejects external post-login destinations", () => {
    expect(returnPath("//evil.test")).toBe("/");
    expect(returnPath("/\\evil.test")).toBe("/");
    expect(returnPath("https://evil.test")).toBe("/");
    expect(returnPath("/seller/dashboard")).toBe("/seller/dashboard");
  });
});
describe("listing constraints", () => {
  it("accepts the full money range without excess precision", () => {
    for (const v of ["0", "0.00", "1499.99", "9999999999.99"])
      expect(validatePrice(v)).toBe(true);
    for (const v of ["-1", "1.999", "1e3", "10000000000", "NaN", "01.20"])
      expect(validatePrice(v)).toBe(false);
  });
  it("normalizes and deduplicates sizes and tags", () => {
    expect(uniqueList(" s, M,s, ", true)).toEqual(["S", "M"]);
    expect(uniqueList("Linen, linen, COTTON")).toEqual(["linen", "cotton"]);
  });
});
