config.set(process.env.ACT === "true"
  ? {
      browsers: ["AirActChromeHeadless"],
      customLaunchers: {
        AirActChromeHeadless: {
          base: "ChromeHeadless",
          flags: ["--no-sandbox"],
        },
      },
    }
  : {})
