package se.glasshome

import org.json.JSONObject

/** Five-day forecast for Mariestad from Open-Meteo (free, no account). */
object Weather {
    const val PLACE = "Mariestad"
    private const val URL =
        "https://api.open-meteo.com/v1/forecast?latitude=58.71&longitude=13.82" +
            "&current=temperature_2m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&timezone=Europe%2FStockholm&forecast_days=5"

    data class Day(val date: String, val code: Int, val max: Int, val min: Int)
    data class Forecast(val nowTemp: Int, val nowCode: Int, val days: List<Day>)

    fun fetch(): String = Net.text(URL)

    fun parse(json: String): Forecast? = try {
        val o = JSONObject(json)
        val cur = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val dates = d.getJSONArray("time")
        val codes = d.getJSONArray("weather_code")
        val maxs = d.getJSONArray("temperature_2m_max")
        val mins = d.getJSONArray("temperature_2m_min")
        val days = (0 until dates.length()).map {
            Day(dates.getString(it), codes.getInt(it),
                Math.round(maxs.getDouble(it)).toInt(), Math.round(mins.getDouble(it)).toInt())
        }
        Forecast(Math.round(cur.getDouble("temperature_2m")).toInt(), cur.getInt("weather_code"), days)
    } catch (_: Exception) { null }

    fun text(code: Int): String = when (code) {
        0 -> "Klart"
        1 -> "Mestadels klart"
        2 -> "Halvklart"
        3 -> "Mulet"
        45, 48 -> "Dimma"
        in 51..57 -> "Duggregn"
        in 61..67 -> "Regn"
        in 71..77 -> "Snö"
        in 80..82 -> "Regnskurar"
        85, 86 -> "Snöbyar"
        in 95..99 -> "Åska"
        else -> ""
    }

    fun icon(code: Int): Int = when (code) {
        0, 1 -> R.drawable.wx_sun
        2 -> R.drawable.wx_partly
        3 -> R.drawable.wx_cloud
        45, 48 -> R.drawable.wx_fog
        in 51..67, in 80..82 -> R.drawable.wx_rain
        in 71..77, 85, 86 -> R.drawable.wx_snow
        in 95..99 -> R.drawable.wx_storm
        else -> R.drawable.wx_cloud
    }
}
