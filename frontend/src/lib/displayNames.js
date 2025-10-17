export const roleDisplayNames = {
  RetailAdmin: 'Администратор',
  StoreManager: 'Менеджер магазина',
  Contractor: 'Подрядчик',
}

export const getRoleDisplayName = (roleName) => {
  return roleDisplayNames[roleName] || roleName
}

export const urgencyDisplayNames = {
  Emergency: 'Аварийная',
  Urgent: 'Срочная',
  Planned: 'Плановая',
  Customizable: 'Настраиваемая',
}

export const getUrgencyDisplayName = (urgencyName) => {
  return urgencyDisplayNames[urgencyName] || urgencyName
}
