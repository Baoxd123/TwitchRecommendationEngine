package com.laioffer.twitchjupiter.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laioffer.twitchjupiter.entity.response.Game;
import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;

@Service
public class GameService {
    private static final String TOKEN = "Bearer Token";
    private static final String CLIENT_ID = "CLIENT_ID";
    private static final String TOP_GAME_URL = "https://api.twitch.tv/helix/games/top?first=%s";
    private static final String GAME_SEARCH_URL_TEMPLATE = "https://api.twitch.tv/helix/games?name=%s";
    private static final int DEFAULT_GAME_LIMIT = 20;

    //Implement buildGameURL function. It will help generate the correct URL when you call Twitch Game API.
    private String builderGameURL(String url, String gameName, int limit){
        if(gameName.equals("")){
            return String.format(url,limit);
        }else{
            try {
                gameName = URLEncoder.encode(gameName,"UTF-8");
            }catch (UnsupportedEncodingException e){
                e.printStackTrace();
            }
            return String.format(url,gameName);
        }
    }

    private String searchTwitch(String url) throws TwitchException{
        CloseableHttpClient httpclient = HttpClients.createDefault();

        ResponseHandler<String> responseHandler = response -> {
            int responseCode = response.getStatusLine().getStatusCode();
            if(responseCode!=200){
                System.out.println("Response status: " + response.getStatusLine().getReasonPhrase());
                throw new TwitchException("Failed to get result from Twitch API");
            }
            HttpEntity entity = response.getEntity();
            if(entity == null){
                throw new TwitchException("Failed to get result from Twitch API");
            }
            JSONObject obj = new JSONObject(EntityUtils.toString(entity));
            return obj.getJSONArray("data").toString();
        };

        try{
            //Define http request
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization",TOKEN);
            request.setHeader("Client-Id", CLIENT_ID);
            return httpclient.execute(request,responseHandler);
        }catch (IOException e){
            e.printStackTrace();
            throw new TwitchException("Failed to get result from Twitch API");
        }finally {
            try{
                httpclient.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private List<Game> getGameList(String data){
        ObjectMapper mapper = new ObjectMapper();
        try{
            return Arrays.asList(mapper.readValue(data,Game[].class));
        }catch(JsonProcessingException e){
            e.printStackTrace();
            throw new RuntimeException("Failed to parse game data from Twitch API");
        }
    }

    public List<Game> topGames(int limit){
        if (limit <= 0) {
            limit = DEFAULT_GAME_LIMIT;
        }
        return getGameList(searchTwitch(builderGameURL(TOP_GAME_URL,"",limit)));
    }

    public Game searchGame(String gameName){
        List<Game> gameList = getGameList(searchTwitch(builderGameURL(GAME_SEARCH_URL_TEMPLATE, gameName,0)));
        if(gameList.size()!=0){
            return gameList.get(0);
        }
        return null;
    }
}
