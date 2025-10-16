export const roleDisplayNames = {
  RetailAdmin: 'Администратор',
  StoreManager: 'Менеджер магазина',
  Contractor: 'Подрядчик',
}

export const getRoleDisplayName = (roleName) => {
  return roleDisplayNames[roleName] || roleName
}