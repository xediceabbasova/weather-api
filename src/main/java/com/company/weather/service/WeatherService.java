package com.company.weather.service;

import com.company.weather.constants.Constants;
import com.company.weather.dto.WeatherDto;
import com.company.weather.dto.WeatherResponse;
import com.company.weather.model.Weather;
import com.company.weather.repository.WeatherRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.yaml.snakeyaml.scanner.Constant;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@CacheConfig(cacheNames = {"weathers"})
public class WeatherService {
    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);
    private final WeatherRepository weatherRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherService(WeatherRepository repository, RestTemplate restTemplate) {
        this.weatherRepository = repository;
        this.restTemplate = restTemplate;
    }

    @Cacheable(key = "#city")
    public WeatherDto getWeatherByCityName(String city) {

        logger.info("Requested city : " + city);
        Optional<Weather> weatherOptional = weatherRepository.findFirstByRequestedCityNameOrderByUpdatedTimeDesc(city);

//        if (!weatherOptional.isPresent()) {
//            return WeatherDto.convert(getWeatherFromWeatherStack(city));
//        }
//
//        if (weatherOptional.get().getUpdatedTime().isBefore(LocalDateTime.now().minusMinutes(30))) {
//            return WeatherDto.convert(getWeatherFromWeatherStack(city));
//        }
//        return WeatherDto.convert(weatherOptional.get());

        return weatherOptional.map(weather -> {
                    if (weather.getUpdatedTime().isBefore(LocalDateTime.now().minusMinutes(30))) {
                        return WeatherDto.convert(getWeatherFromWeatherStack(city));
                    }
                    return WeatherDto.convert(weather);
                })
                .orElseGet(() -> WeatherDto.convert(getWeatherFromWeatherStack(city)));
    }


    private String getWeatherStackUrl(String city) {
        return Constants.API_URL + Constants.ACCESS_KEY_PARAM + Constants.API_KEY + Constants.QUERY_KEY_PARAM + city;
    }


    private Weather getWeatherFromWeatherStack(String city) {
        ResponseEntity<String> responseEntity = restTemplate.getForEntity(getWeatherStackUrl(city), String.class);

        try {
            WeatherResponse weatherResponse = objectMapper.readValue(responseEntity.getBody(), WeatherResponse.class);
            return saveWeather(city, weatherResponse);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    @CacheEvict(allEntries = true)
    @PostConstruct
    @Scheduled(fixedRateString = "10000")
    public void clearCache() {
        logger.info("Cache cleared");
    }


    private Weather saveWeather(String city, WeatherResponse weatherResponse) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        Weather weather = new Weather(city,
                weatherResponse.location().name(),
                weatherResponse.location().country(),
                weatherResponse.current().temperature(),
                LocalDateTime.now(),
                LocalDateTime.parse(weatherResponse.location().localTime(), dateTimeFormatter));

        return weatherRepository.save(weather);
    }
}
