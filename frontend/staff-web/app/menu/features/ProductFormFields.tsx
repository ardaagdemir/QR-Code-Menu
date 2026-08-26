"use client";

import {
  ALLERGENS,
  uploadProductImage,
  type Allergen,
  type ProductAdmin,
} from "@/lib/api";
import FileUploadField from "@/components/ui/FileUploadField";
import FormField from "@/components/ui/FormField";
import Input from "@/components/ui/Input";
import Textarea from "@/components/ui/Textarea";
import menuStyles from "../menu.module.css";

export const ALLERGEN_LABELS: Record<Allergen, string> = {
  GLUTEN: "Gluten",
  CRUSTACEANS: "Kabuklu deniz ürünleri",
  EGGS: "Yumurta",
  FISH: "Balık",
  PEANUTS: "Yer fıstığı",
  SOYBEANS: "Soya",
  MILK: "Süt",
  TREE_NUTS: "Kuruyemiş",
  CELERY: "Kereviz",
  MUSTARD: "Hardal",
  SESAME: "Susam",
  SULPHITES: "Sülfit",
  LUPIN: "Acı bakla",
  MOLLUSCS: "Yumuşakçalar",
};

export type ProductFormValues = {
  name: string;
  description: string;
  price: string;
  imageUrl: string | null;
  preparationMinutes: string;
  allergens: Allergen[];
};

export function emptyProductFormValues(): ProductFormValues {
  return {
    name: "",
    description: "",
    price: "",
    imageUrl: null,
    preparationMinutes: "",
    allergens: [],
  };
}

export function productFormValuesFromProduct(product: ProductAdmin): ProductFormValues {
  return {
    name: product.name,
    description: product.description ?? "",
    price: (product.basePriceMinorUnits / 100).toFixed(2).replace(".", ","),
    imageUrl: product.imageUrl,
    preparationMinutes: product.estimatedPreparationMinutes?.toString() ?? "",
    allergens: product.allergens,
  };
}

type Props = {
  values: ProductFormValues;
  onChange: (values: ProductFormValues) => void;
};

/** Ürün oluşturma ve düzenleme akışlarının ortak alanları - iki mod da aynı alan setini gösterir. */
export default function ProductFormFields({ values, onChange }: Props) {
  function setValue<Key extends keyof ProductFormValues>(key: Key, value: ProductFormValues[Key]) {
    onChange({ ...values, [key]: value });
  }

  function toggleAllergen(allergen: Allergen) {
    setValue(
      "allergens",
      values.allergens.includes(allergen)
        ? values.allergens.filter((item) => item !== allergen)
        : [...values.allergens, allergen],
    );
  }

  return (
    <div className={menuStyles.productFormGrid}>
      <FormField label="Ürün adı" required>
        {(controlProps) => (
          <Input
            {...controlProps}
            value={values.name}
            onChange={(event) => setValue("name", event.target.value)}
            autoComplete="off"
            required
          />
        )}
      </FormField>
      <FormField label="Fiyat (₺)" required>
        {(controlProps) => (
          <Input
            {...controlProps}
            inputMode="decimal"
            value={values.price}
            onChange={(event) => setValue("price", event.target.value)}
            required
          />
        )}
      </FormField>
      <div className={menuStyles.productFormFullWidth}>
        <FormField label="Açıklama" hint="İsteğe bağlı">
          {(controlProps) => (
            <Textarea
              {...controlProps}
              value={values.description}
              onChange={(event) => setValue("description", event.target.value)}
              rows={3}
            />
          )}
        </FormField>
      </div>
      <FormField label="Hazırlık süresi (dk)" hint="İsteğe bağlı">
        {(controlProps) => (
          <Input
            {...controlProps}
            inputMode="numeric"
            value={values.preparationMinutes}
            onChange={(event) => setValue("preparationMinutes", event.target.value)}
          />
        )}
      </FormField>

      <div className={menuStyles.productFormFullWidth}>
        <FileUploadField
          label="Ürün görseli"
          hint="JPEG, PNG veya WEBP - en fazla 5 MB. İsteğe bağlı."
          accept="image/jpeg,image/png,image/webp"
          value={values.imageUrl}
          onChange={(imageUrl) => setValue("imageUrl", imageUrl)}
          upload={uploadProductImage}
        />
      </div>

      <fieldset className={`${menuStyles.allergenField} ${menuStyles.productFormFullWidth}`}>
        <legend className={menuStyles.allergenLegend}>Alerjenler <span>· İsteğe bağlı</span></legend>
        <div className={menuStyles.allergenGrid}>
          {ALLERGENS.map((allergen) => (
            <label key={allergen} className={menuStyles.allergenOption}>
              <input
                type="checkbox"
                checked={values.allergens.includes(allergen)}
                onChange={() => toggleAllergen(allergen)}
              />
              <span>{ALLERGEN_LABELS[allergen]}</span>
            </label>
          ))}
        </div>
      </fieldset>
    </div>
  );
}
