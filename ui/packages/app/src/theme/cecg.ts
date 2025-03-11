import {
  createBaseThemeOptions,
  createUnifiedTheme,
  palettes,
} from '@backstage/theme';

export const cecgTheme = createUnifiedTheme({
  ...createBaseThemeOptions({
    palette: palettes.dark,
  }),
});
