import { createSelector } from "reselect";

// NOTE: these are "public" settings
export const getIsPublicSharingEnabled = state =>
  state.settings.values["public_sharing"];
export const getIsApplicationEmbeddingEnabled = state =>
  state.settings.values["embedding"];
// Whether or not xrays are enabled on the instance
export const getXraysEnabled = state => state.settings.values["enable_xrays"];

// NOTE: these are admin-only settings
export const getSiteUrl = state => state.settings.values["site-url"];
export const getEmbeddingSecretKey = state =>
  state.settings.values["embedding-secret-key"];

const DEFAULT_LOGO_URL = "app/assets/img/logo.svg";

export const getLogoUrl = state =>
  state.settings.values["application-logo-url"] ||
  state.settings.values.application_logo_url ||
  DEFAULT_LOGO_URL;

export const getApplicationColors = state =>
  state.settings.values["application-colors"] ||
  state.settings.values.application_colors;

export const getApplicationName = state =>
  state.settings.values["application-name"] ||
  state.settings.values.application_name;

export const getHasCustomColors = createSelector(
  [getApplicationColors],
  applicationColors => Object.keys(applicationColors || {}).length > 0,
);

export const getHasCustomLogo = createSelector(
  [getLogoUrl],
  logoUrl => logoUrl !== DEFAULT_LOGO_URL,
);

export const getIsWhitelabeled = createSelector(
  [getHasCustomLogo, getHasCustomColors],
  (hasCustomLogo, hasCustomColors) => hasCustomLogo || hasCustomColors,
);
