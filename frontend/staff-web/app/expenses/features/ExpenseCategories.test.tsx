import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/ToastProvider";
import { ApiError, type ExpenseCategory } from "@/lib/api";
import ExpenseCategories from "./ExpenseCategories";

const createExpenseCategory = vi.hoisted(() => vi.fn());
const updateExpenseCategory = vi.hoisted(() => vi.fn());
const deactivateExpenseCategory = vi.hoisted(() => vi.fn());
const activateExpenseCategory = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return {
    ...actual,
    createExpenseCategory,
    updateExpenseCategory,
    deactivateExpenseCategory,
    activateExpenseCategory,
  };
});

function category(overrides: Partial<ExpenseCategory> = {}): ExpenseCategory {
  return { id: "category-1", name: "Kira", active: true, ...overrides };
}

function renderCategories(props: Partial<React.ComponentProps<typeof ExpenseCategories>> = {}) {
  const onCategoryCreated = vi.fn();
  const onCategoryRenamed = vi.fn();
  const onCategoryDeactivated = vi.fn();
  const onCategoryActivated = vi.fn();
  render(
    <ToastProvider>
      <ExpenseCategories
        categories={[category()]}
        isBusinessAdmin={true}
        onCategoryCreated={onCategoryCreated}
        onCategoryRenamed={onCategoryRenamed}
        onCategoryDeactivated={onCategoryDeactivated}
        onCategoryActivated={onCategoryActivated}
        {...props}
      />
    </ToastProvider>,
  );
  return { onCategoryCreated, onCategoryRenamed, onCategoryDeactivated, onCategoryActivated };
}

beforeEach(() => {
  createExpenseCategory.mockReset();
  updateExpenseCategory.mockReset();
  deactivateExpenseCategory.mockReset();
  activateExpenseCategory.mockReset();
});

describe("ExpenseCategories - aksiyon görünürlüğü", () => {
  it("aktif kategori için Pasife Al gösterir, Aktifleştir göstermez", () => {
    renderCategories({ categories: [category({ active: true })] });

    expect(screen.getByRole("button", { name: /Kira kategorisini devre dışı bırak/ })).toBeTruthy();
    expect(screen.queryByRole("button", { name: /Kira kategorisini aktifleştir/ })).toBeNull();
  });

  it("pasif kategori için Aktifleştir gösterir, Pasife Al göstermez", () => {
    renderCategories({ categories: [category({ active: false })] });

    expect(screen.getByRole("button", { name: /Kira kategorisini aktifleştir/ })).toBeTruthy();
    expect(screen.queryByRole("button", { name: /Kira kategorisini devre dışı bırak/ })).toBeNull();
    expect(screen.getByText(/\(Pasif\)/)).toBeTruthy();
  });

  it("isBusinessAdmin=false iken hiçbir kategori aksiyonu göstermez", () => {
    renderCategories({ isBusinessAdmin: false, categories: [category({ active: true })] });

    expect(screen.queryByRole("button", { name: /yeniden adlandır/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /devre dışı bırak/ })).toBeNull();
  });
});

describe("ExpenseCategories - Yeniden Adlandır", () => {
  it("kaydedince updateExpenseCategory çağırır ve onCategoryRenamed tetiklenir", async () => {
    updateExpenseCategory.mockResolvedValue(category({ name: "Kira Gideri" }));
    const { onCategoryRenamed } = renderCategories();

    fireEvent.click(screen.getByRole("button", { name: /Kira kategorisini yeniden adlandır/ }));
    await screen.findByRole("heading", { name: "Kategoriyi Yeniden Adlandır" });

    const input = screen.getByLabelText(/Kategori adı/) as HTMLInputElement;
    expect(input.value).toBe("Kira");
    fireEvent.change(input, { target: { value: "Kira Gideri" } });
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));

    await waitFor(() => expect(updateExpenseCategory).toHaveBeenCalledWith("category-1", "Kira Gideri"));
    await waitFor(() => expect(onCategoryRenamed).toHaveBeenCalledWith(category({ name: "Kira Gideri" })));
    await waitFor(() => expect(screen.queryByRole("heading", { name: "Kategoriyi Yeniden Adlandır" })).toBeNull());
  });

  it("409 çakışmasında diyalog açık kalır ve özel hata mesajı gösterilir", async () => {
    updateExpenseCategory.mockRejectedValue(new ApiError("Conflict", 409));
    renderCategories();

    fireEvent.click(screen.getByRole("button", { name: /Kira kategorisini yeniden adlandır/ }));
    await screen.findByRole("heading", { name: "Kategoriyi Yeniden Adlandır" });
    fireEvent.change(screen.getByLabelText(/Kategori adı/), { target: { value: "Elektrik" } });
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));

    await screen.findByText("Bu isimde bir kategori zaten var.");
    expect(screen.getByRole("heading", { name: "Kategoriyi Yeniden Adlandır" })).toBeTruthy();
  });
});

describe("ExpenseCategories - Pasife Al / Aktifleştir", () => {
  it("Pasife Al onaylanınca deactivateExpenseCategory çağırır", async () => {
    deactivateExpenseCategory.mockResolvedValue(undefined);
    const { onCategoryDeactivated } = renderCategories({ categories: [category({ active: true })] });

    fireEvent.click(screen.getByRole("button", { name: /Kira kategorisini devre dışı bırak/ }));
    const confirmDialog = await screen.findByRole("dialog");
    fireEvent.click(within(confirmDialog).getByRole("button", { name: "Devre Dışı Bırak" }));

    await waitFor(() => expect(deactivateExpenseCategory).toHaveBeenCalledWith("category-1"));
    await waitFor(() => expect(onCategoryDeactivated).toHaveBeenCalledWith("category-1"));
  });

  it("Aktifleştir tıklanınca onay istemeden activateExpenseCategory çağırır", async () => {
    activateExpenseCategory.mockResolvedValue(undefined);
    const { onCategoryActivated } = renderCategories({ categories: [category({ active: false })] });

    fireEvent.click(screen.getByRole("button", { name: /Kira kategorisini aktifleştir/ }));

    await waitFor(() => expect(activateExpenseCategory).toHaveBeenCalledWith("category-1"));
    await waitFor(() => expect(onCategoryActivated).toHaveBeenCalledWith("category-1"));
  });
});
