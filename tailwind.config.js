const colors = require('tailwindcss/colors')
const radix = require('@radix-ui/colors')

function exposeColorsToCssVars ({ addBase, theme }) {
  function extractColorVars (colorObj, colorGroup = '') {
    return Object.keys(colorObj).reduce((vars, colorKey) => {
      const value = colorObj[colorKey]

      const newVars =
        typeof value === 'string'
          ? { [`--color${colorGroup}-${colorKey}`]: value }
          : extractColorVars(value, `-${colorKey}`)

      return { ...vars, ...newVars }
    }, {})
  }

  addBase({
    ':root': extractColorVars(theme('colors')),
  })
}

function buildColor(color, custom) {
  const base = custom || colors[color] || {}

  for (const [xName, xValue] of Object.entries(radix[color] || {})) {
    const regexResult = xName.match(/\d+$/) 
    if (!regexResult) { continue; } 
    const xStep = regexResult[0]
    base[xStep] = xValue
  }

  return base
}

module.exports = {
  darkMode: 'class',
  content: [
    './src/**/*.js',
    './src/**/*.cljs',
    './resources/**/*.html'
  ],
  safelist: [
    'bg-black', 'bg-white',
    { pattern: /bg-(gray|red|yellow|green|blue|orange|indigo|rose|purple|pink)-(100|200|300|400|500|600|700|800|900)/ },
    { pattern: /text-(gray|red|yellow|green|blue|orange|indigo|rose|purple|pink)-(100|200|300|400|500|600|700|800|900)/ },
    { pattern: /columns-([1-9]|1[0-2])|(auto|3xs|2xs|xs|sm|md|lg|xl)|([2-7]xl)/ },
    { pattern: /bg-(mauve|slate|sage|olive|sand|tomato|red|crimson|pink|plum|purple|violet|indigo|blue|sky|cyan|teal|mint|green|grass|lime|yellow|amber|orange|brown)(dark)?-(1|2|3|4|5|6|7|8|9|10|11|12)/ },
    { pattern: /shadow-(mauve|slate|sage|olive|sand|tomato|red|crimson|pink|plum|purple|violet|indigo|blue|sky|cyan|teal|mint|green|grass|lime|yellow|amber|orange|brown)(dark)?-(1|2|3|4|5|6|7|8|9|10|11|12)/ },
    { pattern: /text-(mauve|slate|sage|olive|sand|tomato|red|crimson|pink|plum|purple|violet|indigo|blue|sky|cyan|teal|mint|green|grass|lime|yellow|amber|orange|brown)(dark)?-(11|12)/ },
    { pattern: /border-(mauve|slate|sage|olive|sand|tomato|red|crimson|pink|plum|purple|violet|indigo|blue|sky|cyan|teal|mint|green|grass|lime|yellow|amber|orange|brown)(dark)?-(4|5|6|7|8)/ },
  ],
  plugins: [
    require('@tailwindcss/forms'),
    require('@tailwindcss/typography'),
    require('@tailwindcss/aspect-ratio'),
    require('@tailwindcss/line-clamp'),
    exposeColorsToCssVars
  ],
  theme: {
    extend: {
      fontSize: {
        '2xs': ['0.625rem', '0.875rem']
      },
      animation: {
        'spin-reverse': 'spin 2s linear infinite reverse',
      },
      spacing: {
        '128': '32rem',
        '144': '36rem'
      },
      scale: {
        '200': '2',
        '250': '2.5',
        '300': '3',
        '400': '4',
      }
    },
    colors: {
      // Tailwind colors
      black: colors.black,
      current: 'currentColor',
      rose: colors.rose,
      transparent: 'transparent',
      white: colors.white,

      // Radix colors
      amber: buildColor("amber"),
      blue: buildColor("blue"),
      bronze: buildColor("bronze"),
      brown: buildColor("brown"),
      crimson: buildColor("crimson"),
      cyan: buildColor("cyan"),
      gold: buildColor("gold"),
      grass: buildColor("grass"),
      green: buildColor("green"),
      lime: buildColor("lime"),
      mauve: buildColor("mauve"),
      mint: buildColor("mint"),
      olive: buildColor("olive"),
      orange: buildColor("orange"),
      pink: buildColor("pink"),
      plum: buildColor("plum"),
      purple: buildColor("purple"),
      red: buildColor("red"),
      sage: buildColor("sage"),
      sand: buildColor("sand"),
      sky: buildColor("sky"),
      slate: buildColor("slate"),
      teal: buildColor("teal"),
      tomato: buildColor("tomato"),
      violet: buildColor("violet"),
        
      // Custom colors
      gray: buildColor("gray", colors.neutral),
      yellow: buildColor("yellow", colors.amber),
      indigo: buildColor("indigo", {
        50: '#f0f9ff',
        100: '#e0f2fe',
        200: '#bae6fd',
        300: '#7dd3fc',
        400: '#38bdf8',
        500: '#0ea5e9',
        600: '#0284c7',
        700: '#005b8a',
        800: '#075985',
        900: '#0c4a6e',
      }),

      tomatodark: buildColor("tomatoDark"),
      reddark: buildColor("redDark"),
      crimsondark: buildColor("crimsonDark"),
      pinkdark: buildColor("pinkDark"),
      plumdark: buildColor("plumDark"),
      purpledark: buildColor("purpleDark"),
      violetdark: buildColor("violetDark"),
      skydark: buildColor("skyDark"),
      indigodark: buildColor("indigoDark"),
      bluedark: buildColor("blueDark"),
      cyandark: buildColor("cyanDark"),
      mintdark: buildColor("mintDark"),
      tealdark: buildColor("tealDark"),
      greendark: buildColor("greenDark"),
      limedark: buildColor("limeDark"),
      grassdark: buildColor("grassDark"),
      yellowdark: buildColor("yellowDark"),
      amberdark: buildColor("amberDark"),
      orangedark: buildColor("orangeDark"),
      browndark: buildColor("brownDark"),
      graydark: buildColor("grayDark"),
      mauvedark: buildColor("mauveDark"),
      slatedark: buildColor("slateDark"),
      sagedark: buildColor("sageDark"),
      olivedark: buildColor("oliveDark"),
      sanddark: buildColor("sandDark"),
      golddark: buildColor("goldDark"),
      bronzedark: buildColor("bronzeDark"),
    }
  }
}
