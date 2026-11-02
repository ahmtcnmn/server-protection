// JWT'yi tarayıcıda saklar. Manager refresh endpoint'i sunmadığı için token süresi
// dolunca (app.jwt.expirationMs) kullanıcı tekrar login sayfasına yönlendirilir.
const TOKEN_KEY = 'sentinel.token';

function read(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

interface JwtPayload {
  sub?: string;
  exp?: number;
}

function decode(token: string): JwtPayload | null {
  try {
    const part = token.split('.')[1];
    const json = atob(part.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

export function getToken(): string | null {
  const token = read();
  if (!token) return null;
  const payload = decode(token);
  // Süresi dolmuş token'ı hiç göndermeyelim
  if (payload?.exp && payload.exp * 1000 <= Date.now()) {
    clearToken();
    return null;
  }
  return token;
}

export function setToken(token: string) {
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // depolama kapalıysa oturum sayfa yenilenene kadar sürmez; yapacak bir şey yok
  }
}

export function clearToken() {
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    // yok say
  }
}

export function getUsername(): string | null {
  const token = getToken();
  return token ? decode(token)?.sub ?? null : null;
}
