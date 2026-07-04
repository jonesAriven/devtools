import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const FAVORITES_KEY = 'portal_favorites'
const COLLAPSED_KEY = 'portal_collapsed_categories'

export const useFavoritesStore = defineStore('favorites', () => {
  const favorites = ref<string[]>(JSON.parse(localStorage.getItem(FAVORITES_KEY) || '[]'))
  const collapsedCategories = ref<string[]>(JSON.parse(localStorage.getItem(COLLAPSED_KEY) || '[]'))

  const isFavorite = (id: string) => favorites.value.includes(id)

  function toggleFavorite(id: string) {
    const index = favorites.value.indexOf(id)
    if (index > -1) {
      favorites.value.splice(index, 1)
    } else {
      favorites.value.push(id)
    }
    localStorage.setItem(FAVORITES_KEY, JSON.stringify(favorites.value))
  }

  const isCollapsed = (category: string) => collapsedCategories.value.includes(category)

  function toggleCollapse(category: string) {
    const index = collapsedCategories.value.indexOf(category)
    if (index > -1) {
      collapsedCategories.value.splice(index, 1)
    } else {
      collapsedCategories.value.push(category)
    }
    localStorage.setItem(COLLAPSED_KEY, JSON.stringify(collapsedCategories.value))
  }

  return {
    favorites,
    collapsedCategories,
    isFavorite,
    toggleFavorite,
    isCollapsed,
    toggleCollapse
  }
})
