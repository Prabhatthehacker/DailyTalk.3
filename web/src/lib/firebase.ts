import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getDatabase } from 'firebase/database';

const cfg = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY as string | undefined,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN as string | undefined,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID as string | undefined,
  databaseURL: import.meta.env.VITE_FIREBASE_DATABASE_URL as string | undefined,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID as string | undefined,
  appId: import.meta.env.VITE_FIREBASE_APP_ID as string | undefined,
};

export const firebaseConfigured = Boolean(cfg.apiKey && cfg.projectId && cfg.appId && cfg.databaseURL);

const app = initializeApp(firebaseConfigured ? cfg : { apiKey: 'unset', projectId: 'unset', appId: 'unset', databaseURL: 'https://unset.firebaseio.com' });
export const auth = getAuth(app);
export const db = getDatabase(app);
