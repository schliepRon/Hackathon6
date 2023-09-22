package classes.result;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import javax.annotation.Generated;
import java.io.Serializable;
import java.util.List;

@Generated("jsonschema2pojo")
public class Result implements Serializable
{

    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("players")
    @Expose
    private List<ResultPlayer> players = null;
    @SerializedName("board")
    @Expose
    private List<List<String>> board = null;
//
    @SerializedName("type")
    @Expose
    private String type;
    @SerializedName("self")
    @Expose
    private String self;


    @SerializedName("overview")
    @Expose
    private List<String> overview = null;
    private final static long serialVersionUID = 8243041135500189102L;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<ResultPlayer> getPlayers() {
        return players;
    }

    public void setPlayers(List<ResultPlayer> players) {
        this.players = players;
    }

    public List<String> getOverview() {
        return overview;
    }

    public void setOverview(List<String> overview) {
        this.overview = overview;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSelf() {
        return self;
    }

    public void setSelf(String self) {
        this.self = self;
    }

    public List<List<String>> getBoard() {
        return board;
    }

    public void setBoard(List<List<String>> board) {
        this.board = board;
    }


}