package com.laioffer.twitchjupiter.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laioffer.twitchjupiter.entity.response.Game;
import com.laioffer.twitchjupiter.entity.response.Item;
import com.laioffer.twitchjupiter.entity.response.ItemType;
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
import java.util.*;

@Service
public class GameService {
    private static final String TOKEN = "Bearer Token";
    private static final String CLIENT_ID = "CLIENT_ID";
    private static final String TOP_GAME_URL = "https://api.twitch.tv/helix/games/top?first=%s";
    private static final String GAME_SEARCH_URL_TEMPLATE = "https://api.twitch.tv/helix/games?name=%s";
    private static final int DEFAULT_GAME_LIMIT = 20;
    private static final String STREAM_SEARCH_URL_TEMPLATE = "https://api.twitch.tv/helix/streams?game_id=%s&first=%s";
    private static final String VIDEO_SEARCH_URL_TEMPLATE = "https://api.twitch.tv/helix/videos?game_id=%s&first=%s";
    private static final String CLIP_SEARCH_URL_TEMPLATE = "https://api.twitch.tv/helix/clips?game_id=%s&first=%s";
    private static final String TWITCH_BASE_URL = "https://www.twitch.tv/";
    private static final int DEFAULT_SEARCH_LIMIT = 20;

    // Similar to buildGameURL, build Search URL that will be used when calling Twitch API. e.g. https://api.twitch.tv/helix/clips?game_id=12924.
    private String buildSearchURL(String url, String gameId, int limit) {
        try {
            gameId = URLEncoder.encode(gameId, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return String.format(url, gameId, limit);
    }

    // Similar to getGameList, convert the json data returned from Twitch to a list of Item objects.
    private List<Item> getItemList(String data) throws TwitchException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return Arrays.asList(mapper.readValue(data, Item[].class));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new TwitchException("Failed to parse item data from Twitch API");
        }
    }

    // Returns the top x streams based on game ID.
    private List<Item> searchStreams(String gameId, int limit) throws TwitchException {
        List<Item> streams = getItemList(searchTwitch(buildSearchURL(STREAM_SEARCH_URL_TEMPLATE, gameId, limit)));
        for (Item item : streams) {
            item.setType(ItemType.STREAM);
            item.setUrl(TWITCH_BASE_URL + item.getBroadcasterName());
        }
        return streams;
    }

    // Returns the top x clips based on game ID.
    private List<Item> searchClips(String gameId, int limit) throws TwitchException {
        List<Item> clips = getItemList(searchTwitch(buildSearchURL(CLIP_SEARCH_URL_TEMPLATE, gameId, limit)));
        for (Item item : clips) {
            item.setType(ItemType.CLIP);
        }
        return clips;
    }

    // Returns the top x videos based on game ID.
    private List<Item> searchVideos(String gameId, int limit) throws TwitchException {
        List<Item> videos = getItemList(searchTwitch(buildSearchURL(VIDEO_SEARCH_URL_TEMPLATE, gameId, limit)));
        for (Item item : videos) {
            item.setType(ItemType.VIDEO);
        }
        return videos;
    }

    private List<Item> searchByType(String gameId, ItemType type, int limit) throws TwitchException {
        List<Item> items = Collections.emptyList();

        switch (type) {
            case STREAM:
                items = searchStreams(gameId, limit);
                break;
            case VIDEO:
                items = searchVideos(gameId, limit);
                break;
            case CLIP:
                items = searchClips(gameId, limit);
                break;
        }

        // Update gameId for all items. GameId is used by recommendation function
        for (Item item : items) {
            item.setGameId(gameId);
        }
        return items;
    }

    public Map<String, List<Item>> searchItems(String gameId) throws TwitchException {
        Map<String, List<Item>> itemMap = new HashMap<>();
        for (ItemType type : ItemType.values()) {
            itemMap.put(type.toString(), searchByType(gameId, type, DEFAULT_SEARCH_LIMIT));
        }
        return itemMap;
    }


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
