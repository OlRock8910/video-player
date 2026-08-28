import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.pcbuilder.game',
  appName: 'PC Builder',
  webDir: 'dist',
  // The game ships entirely inside the APK — no network is used at runtime.
  server: {
    androidScheme: 'https',
  },
  android: {
    // The 3D canvas needs the hardware path; debuggable builds only.
    webContentsDebuggingEnabled: false,
    backgroundColor: '#05070aff',
    allowMixedContent: false,
  },
  plugins: {
    SplashScreen: {
      launchAutoHide: true,
      backgroundColor: '#05070a',
      showSpinner: false,
    },
  },
};

export default config;
