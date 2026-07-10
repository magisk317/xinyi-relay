import { createRequire } from 'node:module'

// Plugins live in webui/node_modules; this config sits at the frontend root so
// its base path covers both webui/src and the shared/ sibling directory.
const req = createRequire(new URL('./webui/package.json', import.meta.url))
const js = req('@eslint/js')
const tsParser = req('@typescript-eslint/parser')
const tsPlugin = req('@typescript-eslint/eslint-plugin')
const reactHooks = req('eslint-plugin-react-hooks')

export default [
  js.configs.recommended,
  {
    files: ['webui/src/**/*.{ts,tsx}', 'shared/**/*.ts'],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaVersion: 2022,
        sourceType: 'module',
        ecmaFeatures: { jsx: true }
      }
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
      'react-hooks': reactHooks
    },
    rules: {
      ...tsPlugin.configs.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      'no-undef': 'off',
      'no-console': ['warn', { allow: ['warn', 'error'] }]
    }
  }
]
