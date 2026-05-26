import request from './request'

export const authApi = {
  login(data) { return request.post('/auth/login', data) },
  info() { return request.get('/auth/info') },
  register(data) { return request.post('/auth/register', data) }
}

export const serverApi = {
  list() { return request.get('/server/list') },
  getById(id) { return request.get(`/server/${id}`) },
  create(data) { return request.post('/server', data) },
  update(id, data) { return request.put(`/server/${id}`, data) },
  delete(id) { return request.delete(`/server/${id}`) },
  deploy(id) { return request.post(`/server/${id}/deploy`) },
  restart(id) { return request.post(`/server/${id}/restart`) },
  status(id) { return request.get(`/server/${id}/status`) }
}

export const clientApi = {
  list(serverId) { return request.get('/client/list', { params: { serverId } }) },
  getById(id) { return request.get(`/client/${id}`) },
  create(data) { return request.post('/client', data) },
  update(id, data) { return request.put(`/client/${id}`, data) },
  delete(id) { return request.delete(`/client/${id}`) },
  deploy(id) { return request.post(`/client/${id}/deploy`) },
  restart(id) { return request.post(`/client/${id}/restart`) },
  status(id) { return request.get(`/client/${id}/status`) },
  testSsh(id) { return request.post(`/client/${id}/test-ssh`) }
}

export const tunnelApi = {
  list(clientId) { return request.get('/tunnel/list', { params: { clientId } }) },
  getById(id) { return request.get(`/tunnel/${id}`) },
  create(data) { return request.post('/tunnel', data) },
  update(id, data) { return request.put(`/tunnel/${id}`, data) },
  delete(id) { return request.delete(`/tunnel/${id}`) }
}

export const deployApi = {
  previewFrps(serverId) { return request.get(`/deploy/preview/frps/${serverId}`) },
  previewFrpc(clientId) { return request.get(`/deploy/preview/frpc/${clientId}`) },
  deployFrps(serverId) { return request.post(`/deploy/frps/${serverId}`) },
  deployFrpc(clientId) { return request.post(`/deploy/frpc/${clientId}`) },
  deployAll() { return request.post('/deploy/all') }
}
