import React, { useEffect, useState } from 'react'
import api from '../api/axios'

export default function Profile() {
  const [profile, setProfile] = useState(null)
  const [err, setErr] = useState(null)

  useEffect(() => {
    let active = true
    api.get('/api/users/me')
      .then(r => { if (active) setProfile(r.data) })
      .catch(e => { if (active) setErr(e.response?.data || e.message) })
    return () => { active = false }
  }, [])

  if (err) return <div className="p-8 text-red-600">{err}</div>
  if (!profile) return <div className="p-8">Загрузка профиля...</div>
  return (
    <div className="p-8">
      <h2 className="text-xl font-semibold mb-4">Профиль</h2>
      <div>Login: {profile.login}</div>
      <div>RoleID: {profile.roleID}</div>
    </div>
  )
}