import { definePreset } from '@primeuix/themes';
import AuraBase from '@primeuix/themes/aura/base';
import AuraButton from '@primeuix/themes/aura/button';
import AuraDialog from '@primeuix/themes/aura/dialog';
import AuraInputText from '@primeuix/themes/aura/inputtext';
import AuraSelect from '@primeuix/themes/aura/select';
import AuraTag from '@primeuix/themes/aura/tag';
import AuraTextarea from '@primeuix/themes/aura/textarea';

export const SgpPreset = definePreset(AuraBase, {
  semantic: {
    primary: {
      50: '#eff6ff',
      100: '#dbeafe',
      200: '#bfdbfe',
      300: '#93c5fd',
      400: '#60a5fa',
      500: '#2563eb',
      600: '#1d4ed8',
      700: '#1e40af',
      800: '#1e3a8a',
      900: '#172554',
      950: '#0b1739',
    },
  },
  components: {
    button: AuraButton,
    dialog: AuraDialog,
    inputtext: AuraInputText,
    select: AuraSelect,
    tag: AuraTag,
    textarea: AuraTextarea,
  },
});
