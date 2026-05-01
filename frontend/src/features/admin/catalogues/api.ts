import { api } from '@/shared/api/client';

export type ServiceCategory = 'LAB' | 'IMAGING' | 'ECO' | 'EMERGENCY' | 'DRUG';

export type DrugPart = {
  genericName: string | null;
  dosageForm: string | null;
  strength: string | null;
  unit: string | null;
  controlled: boolean;
  supplier: string | null;
  barcode: string | null;
};

export type ServiceItem = {
  id: string;
  category: ServiceCategory;
  code: string;
  nameEn: string;
  nameAr: string | null;
  description: string | null;
  fee: number | null;
  currency: string;
  sortOrder: number;
  active: boolean;
  forwardTo: ServiceCategory | null;
  drug: DrugPart | null;
};

export type AddItemBody = {
  category: ServiceCategory;
  code: string;
  nameEn: string;
  nameAr?: string;
  description?: string;
  fee?: number;
  currency?: string;
  sortOrder?: number;
  forwardTo?: ServiceCategory | null;
  drug?: Partial<DrugPart>;
};

export type UpdateItemBody = {
  nameEn: string;
  nameAr?: string;
  description?: string;
  fee?: number;
  currency?: string;
  sortOrder?: number;
  forwardTo?: ServiceCategory | null;
  drug?: Partial<DrugPart>;
};

export async function listItems(category: ServiceCategory, activeOnly?: boolean): Promise<ServiceItem[]> {
  const res = await api.get('/catalogue/items', { params: { category, activeOnly } });
  return res.data;
}

export async function addItem(body: AddItemBody): Promise<ServiceItem> {
  const res = await api.post('/catalogue/items', body);
  return res.data;
}

export async function updateItem(id: string, body: UpdateItemBody): Promise<ServiceItem> {
  const res = await api.put(`/catalogue/items/${id}`, body);
  return res.data;
}

export async function archiveItem(id: string): Promise<ServiceItem> {
  const res = await api.put(`/catalogue/items/${id}/archive`);
  return res.data;
}

export async function unarchiveItem(id: string): Promise<ServiceItem> {
  const res = await api.put(`/catalogue/items/${id}/unarchive`);
  return res.data;
}
