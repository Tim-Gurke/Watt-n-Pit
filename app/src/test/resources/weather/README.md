# Recorded weather API contract

Public Open-Meteo responses captured on 24 October 2026 using the public example location Berlin city centre (52.52, 13.41), `forecast_days=15`, `past_days=7`, `timeformat=unixtime`, `timezone=auto`. These coordinates are synthetic test input and are unrelated to the user’s location. No user settings, identities or measurements are included.

Base: daily `weather_code,sunshine_duration,sunrise,sunset,shortwave_radiation_sum`; hourly `shortwave_radiation,sunshine_duration`. Tilted: hourly `global_tilted_irradiance`, `tilt=35`, `azimuth=0`.

The responses keep `utc_offset_seconds=7200` for daily boundaries, including beyond the 25 October switch to winter time. Native parsing must retain consecutive calendar labels while hourly bins follow the actual IANA timezone (25-hour Sunday).

Attribution: Open-Meteo, CC BY 4.0. Documentation: https://open-meteo.com/en/docs
