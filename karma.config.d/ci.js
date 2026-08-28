config.set(process.env.CI === "true" || process.env.ACT === "true"
  ? {
      browsers: ["AirCiChromeHeadless"],
      customLaunchers: {
        AirCiChromeHeadless: {
          base: "ChromeHeadless",
          flags: ["--no-sandbox"],
        },
      },
    }
  : {})
