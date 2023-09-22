import classes.Coordinate;
import classes.board.Direction;
import classes.board.Furniture;
import classes.helper.Type;
import classes.init.Init;
import classes.result.Result;
import classes.result.ResultPlayer;
import classes.round.Round;
import com.google.gson.Gson;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.engineio.client.transports.WebSocket;
import org.json.JSONArray;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Wolkenbot {
    public static final String SECRET = "c9221484-f005-4228-9339-355e95a43fa2"; //Das Secret des Bot
    public static final String GAMESERVER = "https://games.uhno.de"; //URL zum Gameserver

    //Verschiedene Arten von Rueckgaben
    public static final String INIT = "INIT";

    public static final String SET = "SET";
    public static final String ROUND = "ROUND";
    public static final String RESULT = "RESULT";

    //Parser fuer JSON-String -> Java-Objekte
    public static final Gson gson = new Gson();


    public static void main(String... args) {
        if (SECRET == null || SECRET.isEmpty()) {
            System.err.println("Was mit Secret?");
            return;
        }


        URI uri = URI.create(GAMESERVER);
        IO.Options options = IO.Options.builder()
                .setTransports(new String[]{WebSocket.NAME})
                .build();

        Socket socket = IO.socket(uri, options);

        //ab hier ist der Socket erstellt, aber noch nichts mit gemacht worden
        //wir registrieren als naechstes die entsprechenden events

        socket.on("connect", (event) -> {
            System.out.println("connect");

            socket.emit("authenticate", new Object[]{SECRET}, (response) -> {
                System.out.println("success? ");
                Boolean success = (Boolean) response[0];
                System.out.println("\t" + success);
            });
        });

        socket.on("disconnect", (event) -> {
            System.out.println("disconnect");
        });

        Map<String, Integer> winLoss = new LinkedHashMap<>();
        winLoss.put("WIN", 0);
        winLoss.put("LOSS", 0);
        Map<String, String[][]> boardMap = new HashMap<>();
//
//        Map<Coordinate, Integer> trefferCache = new LinkedHashMap<>();
//        Map<String, List<Coordinate>> schachbrett = new HashMap<>();
//        Map<String, Coordinate> lastShot = new HashMap<>();
//        Map<String, Integer> winLoss = new HashMap<>();
//
        Map<String, List<Coordinate>> meineTreffer = new HashMap<>();
        Map<String, List<Coordinate>> andereTreffer = new HashMap<>();

        Map<String, Integer> lastPlayedBoard = new LinkedHashMap<>();
        Map<String, Integer[]> offeneBoards = new LinkedHashMap<>();
        Map<String, List<List<Coordinate>>> leftCoordsToShootSection = new HashMap<>();
        Map<String, List<Coordinate>> leftCoordsToShootAll = new HashMap<>();
        Map<String, Integer> logsSize = new LinkedHashMap<>();
//
//        winLoss.put("WIN", 0);
//        winLoss.put("LOSS", 0);
//
//        boards.add(myBoard);
//        boards.add(enemyBoard);

        Map<String, String> symbols = new HashMap<>();
        socket.on("data", (data) -> {
            //der aufbau von data ist hier wie folgt:
            //in data[0] ist immer der json-response
            //in data[1] ist der "Ack", den wir brauchen zum antworten

            String json = String.valueOf(data[0]);

            Type type = gson.fromJson(json, Type.class);

            if (type.getType().equalsIgnoreCase(INIT)) {
                Init object = gson.fromJson(json, Init.class);

                if (object != null) {
                    symbols.put(object.getId(), object.getPlayers().stream()
                            .filter(p -> p.getId().equals(object.getSelf())).findAny().get().getSymbol());
                }
                String[][] board = new String[9][9];
                Integer[] offene = new Integer[9];
                System.out.println("INIT! " + object.getId());
                List<List<Coordinate>> sectionCoords = new ArrayList<>();
                List<Coordinate> allCoords = new ArrayList<>();
                for (int x = 0; x < 9; x++) {
                    sectionCoords.add(x, new ArrayList<>());
                    for (int y = 0; y < 9; y++) {
                        board[x][y] = "";
                        allCoords.add(new Coordinate(x, y));
                        sectionCoords.get(x).add(new Coordinate(x, y));
                    }
                    offene[x] = 0;
                }
                leftCoordsToShootAll.put(object.getId(), allCoords);
                leftCoordsToShootSection.put(object.getId(), sectionCoords);
                offeneBoards.put(object.getId(), offene);
                boardMap.put(object.getId(), board);
                lastPlayedBoard.put(object.getId(), -1);
            } else if (type.getType().equalsIgnoreCase(ROUND)) {

                //bei ROUND muessen wir ja antworten, also holen wir uns das "Ack"
                //in anderen worten: callback, rueckkanal, "der wo die antwort hin muss", ...
                Ack ack = (Ack) data[data.length - 1];
                Round object = gson.fromJson(json, Round.class);
                System.out.println("RUNDE! " + object.getId());


                for (int x = 0; x < 9; x++) {
                    for (int y = 0; y < 9; y++) {
                        if (
                                object.getOverview().get(x).equalsIgnoreCase("x") ||
                                        object.getOverview().get(x).equalsIgnoreCase("o") ||
                                        object.getOverview().get(x).equals("-")
                        ) {
                            leftCoordsToShootSection.get(object.getId()).get(x).clear();
                            continue;
                        }
                        if (
                                object.getBoard().get(x).get(y).equalsIgnoreCase("x") ||
                                        object.getBoard().get(x).get(y).equalsIgnoreCase("o")
                        ) {
                            leftCoordsToShootSection.get(object.getId()).get(x).remove(new Coordinate(x, y));
                        }
                    }

                }

                Coordinate nextShot = getNextShot(object.getOverview(),
                        object.getPlayers().stream().filter(p -> p.getId().equals(object.getSelf())).findFirst().get().getSymbol(),
                        object.getBoard(),
                        object.getForcedSelection(),
                        object.getId(), offeneBoards, lastPlayedBoard, leftCoordsToShootSection, leftCoordsToShootAll);
                JSONArray jsonArray = new JSONArray();
                jsonArray.put(nextShot.getX());
                jsonArray.put(nextShot.getY());
                System.out.println("\tWir schicken: " + jsonArray);

                lastPlayedBoard.replace(object.getId(), nextShot.getX());

                //hier rufen wir dann auf dem Ack entsprechend unser ergebnis auf
                ack.call(jsonArray);


            } else if (type.getType().equalsIgnoreCase(RESULT)) {
                System.out.println("Runde vorbei!");
                ResultPlayer myself = null;
                boolean bug = false;
                try {
                    Result object = gson.fromJson(json, Result.class);
                    myself =
                            object.getPlayers().stream()
                                    .filter(resultPlayer -> resultPlayer.getId().equals(object.getSelf()))
                                    .findAny().get();
                    System.out.println(getBoardVis(object.getBoard()));

                    //TREFFER MERKEN
//                    List<Coordinate> treffer = new ArrayList<>();
//                    treffer.addAll(angehittet.get(object.getId()));
//                    treffer.addAll(tot.get(object.getId()));
//                    treffer = treffer.stream().distinct().collect(Collectors.toList());
//                    treffer.forEach(coordinate -> {
//                        int hitCount = trefferCache.getOrDefault(coordinate, 0);
//                        if (trefferCache.containsKey(coordinate)) {
//                            trefferCache.replace(coordinate, ++hitCount);
//                        } else {
//                            trefferCache.put(coordinate, ++hitCount);
//                        }
//                    });
                } catch (Exception ignore) {

                }


                if (myself == null || myself.getScore() > 0) {
                    System.out.println("WIN");
                    winLoss.replace("WIN", winLoss.get("WIN") + 1);
                } else {
                    winLoss.replace("LOSS", winLoss.get("LOSS") + 1);
                }
                System.out.println(String.format("WIN/LOSS (%s/%s) %s", winLoss.get("WIN"), winLoss.get("LOSS"), ((double) winLoss.get("WIN") / (winLoss.get("WIN") + winLoss.get("LOSS"))) * 100 + "%"));
                System.out.println(json);
                if (bug) {
                    System.out.println("BUG!!!!!!!!!!!!!!!!!!!!!!!!!");
                }
            } else {
                System.out.println("unbekannter typ:");
                System.out.println(json);
            }
        });

        //jetzt sind alle events angelegt, wir oeffnen den socket, los gehts!

        socket.open();
    }

    private static Coordinate getNextShot(List<String> overview, String mySymbol, List<List<String>> board,
                                          Integer forcedSelection, String gameID, Map<String, Integer[]> offeneBoards,
                                          Map<String, Integer> lastPlayedBoard,
                                          Map<String, List<List<Coordinate>>> leftCoordsToShootSection,
                                          Map<String, List<Coordinate>> leftCoordsToShootAll) {

        System.out.println(forcedSelection);


        Integer rand = null;

        if (forcedSelection == null) {
            rand = findBestOrWorstSection(mySymbol, overview, true);
            if (rand == null) {
                rand = ThreadLocalRandom.current().nextInt(0, 8);

            }
            if (!overview.get(rand).equals("")) {
                rand = overview.indexOf("");
            }
        }


        int index1 = forcedSelection == null ? rand : forcedSelection;


        List<Coordinate> verfuegbareCoordinaten = new ArrayList<>(leftCoordsToShootSection.get(gameID).get(index1));
        System.out.println("verf. Coords: " + verfuegbareCoordinaten.size());
        Coordinate bestCoord = findBestShotInSection(index1, mySymbol, board, verfuegbareCoordinaten, overview);
        Collections.shuffle(verfuegbareCoordinaten);
        return bestCoord != null ? bestCoord : verfuegbareCoordinaten.stream().findFirst().orElse(new Coordinate(0, 0));
    }

    private static Coordinate findBestShotInSection(int section, String mySymbol, List<List<String>> board,
                                                    List<Coordinate> freeCoordinates, List<String> overview) {
        AtomicReference<Coordinate> bestShot = new AtomicReference<>();
        AtomicInteger bestShotOcc = new AtomicInteger();

        Map<Coordinate, Integer> bestCoords = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            if (!board.get(section).get(i).equalsIgnoreCase(mySymbol)) {
                continue;
            }
//            if (!freeCoordinates.isEmpty()) {
//                Integer bestSection = findBestSection(mySymbol, overview);
//                if (bestSection != null && freeCoordinates.contains(new Coordinate(section, bestSection))) {
//                    return new Coordinate(section, bestSection);
//                }
//            }
            Integer worstSectionForEnemy = findWorstSectionForEnemy(mySymbol, overview, board);
            //TODO find Worst enemy place
            List<Coordinate> bestShots;
            if (shouldDiagonal(i)) {
                bestShots = getCoordsAround(new Coordinate(section, i), false);
            } else {
                bestShots = getCoordsAroundWithoutDiagonal(new Coordinate(section, i), false);
            }
            bestShots.forEach(bs -> {
                if (bestCoords.containsKey(bs)) {
                    bestCoords.replace(bs, bestCoords.get(bs) + 1);
                } else {
                    bestCoords.put(bs, worstSectionForEnemy != null && bs.getX() == worstSectionForEnemy ? 5 : 1);
                }
            });

        }
        bestCoords.forEach((bs, occ) -> {
            if (occ > bestShotOcc.get() && freeCoordinates.contains(bs)) {
                bestShot.set(bs);
                bestShotOcc.set(occ);
            }
        });
        if (bestShot.get() != null) {
            return bestShot.get();
        }
        return null;
    }

    private static boolean shouldDiagonal(int i) {
        return i == 0 || i == 2 || i == 4 || i == 6 || i == 8;
    }

    private static Integer findBestOrWorstSection(String mySymbol, List<String> overview, boolean best) {
        if (!overview.contains(mySymbol)) {
            return null;
        }
        try {
            Map<Integer, Integer> occs = new HashMap<>();
            for (int i = 0; i < 9; i++) {
                if (!overview.get(i).equalsIgnoreCase(mySymbol)) {
                    continue;
                }
                List<Coordinate> coordinates = new ArrayList<>();

                if (shouldDiagonal(i)) {
                    coordinates = getCoordsAround(new Coordinate(i, 2), true).stream().filter(c -> c.getY() == 2).collect(Collectors.toList());
                } else {
                    coordinates = getCoordsAroundWithoutDiagonal(new Coordinate(i, 2), true);
                }
                AtomicInteger result = new AtomicInteger();
                coordinates.forEach(coordinate -> {
                    if (coordinate.getX() < 0 || coordinate.getX() > 8) {
                        return;
                    }
                    if (occs.containsKey(coordinate.getX())) {
                        occs.replace(coordinate.getX(), occs.get(coordinate.getX()) + 1);
                    } else {
                        occs.put(coordinate.getX(), 1);
                    }
                });
            }
            AtomicInteger index = new AtomicInteger(-1);
            AtomicInteger occ = new AtomicInteger(best ? -1 : 9);
            occs.forEach((key, occu) -> {
                if (best && occu > occ.get()) {
                    occ.set(occu);
                    index.set(key);
                } else if (!best && occu < occ.get()) {
                    occ.set(occu);
                    index.set(key);
                }
            });
            return index.get();
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer findWorstSectionForEnemy(String mySymbol, List<String> overview, List<List<String>> board) {
        return findBestOrWorstSection(mySymbol.equalsIgnoreCase("x") ? "O" : "X", overview, false);
    }

    private static int[] getRandomCoord() {
        int randomNum = ThreadLocalRandom.current().nextInt(0, 9);
        int randomNum2 = ThreadLocalRandom.current().nextInt(0, 9);
        return new int[]{randomNum, randomNum2};
    }

    private static String getRandomDirection() {
        int randomNum = ThreadLocalRandom.current().nextInt(0, 2);
        if (randomNum == 1) {
            return Direction.HORIZONTAL.getAlias();
        }
        return Direction.VERTICAL.getAlias();
    }

    private static List<Coordinate> getFullCoordsOfFurniture(Furniture furniture) {

        List<Coordinate> result = new ArrayList<>();

        if (furniture == null) {
            return result;
        }
        result.add(new Coordinate(furniture.getStart()));

        for (int i = 1; i <= furniture.getSize(); i++) {
            if (furniture.getDirection().equals(Direction.HORIZONTAL.getAlias())) {
                result.add(new Coordinate(furniture.getStart()[0] + i, furniture.getStart()[1]));
            } else {
                result.add(new Coordinate(furniture.getStart()[0], furniture.getStart()[1] + i));
            }
        }
        return result;
    }

    public static List<Coordinate> getCoordsAround(Coordinate coord, boolean section) {

        List<Coordinate> result = new ArrayList<>();

        if (section) {

            if (coord.getX() - 1 >= 0) {
                result.add(new Coordinate(coord.getX() - 1, coord.getY()));
            }
            if (coord.getX() + 1 <= 8) {
                result.add(new Coordinate(coord.getX() + 1, coord.getY()));
            }
            if (coord.getX() - 3 >= 0) {
                result.add(new Coordinate(coord.getX() - 3, coord.getY()));
            }
            if (coord.getX() + 3 <= 8) {
                result.add(new Coordinate(coord.getX() + 3, coord.getY()));
            }
            if (coord.getX() - 2 >= 0) {
                result.add(new Coordinate(coord.getX() - 1, coord.getY()));
            }
            if (coord.getX() + 2 <= 8) {
                result.add(new Coordinate(coord.getX() + 1, coord.getY()));
            }
            if (coord.getX() - 4 >= 0) {
                result.add(new Coordinate(coord.getX() - 3, coord.getY()));
            }
            if (coord.getX() + 4 <= 8) {
                result.add(new Coordinate(coord.getX() + 3, coord.getY()));
            }

        } else {

            if (coord.getY() - 1 >= 0) {
                result.add(new Coordinate(coord.getX(), coord.getY() - 1));
            }
            if (coord.getY() + 1 <= 8) {
                result.add(new Coordinate(coord.getX(), coord.getY() + 1));
            }
            if (coord.getY() - 3 >= 0) {
                result.add(new Coordinate(coord.getX(), coord.getY() - 3));
            }
            if (coord.getY() + 3 <= 8) {
                result.add(new Coordinate(coord.getX(), coord.getY() + 3));
            }
            if (coord.getY() - 2 >= 0) {
                result.add(new Coordinate(coord.getX(), coord.getY() - 1));
            }
            if (coord.getY() + 2 <= 8) {
                result.add(new Coordinate(coord.getX(), coord.getY() + 1));
            }
            if (coord.getY() - 4 >= 0) {
                result.add(new Coordinate(coord.getX(), coord.getY() - 3));
            }
            if (coord.getY() + 4 <= 8) {
                result.add(new Coordinate(coord.getX(), coord.getY() + 3));
            }

        }
        result.remove(coord);
        return result;
    }

    public static List<Coordinate> getCoordsAroundWithoutDiagonal(Coordinate coord, boolean section) {

        List<Coordinate> result = new ArrayList<>();

        if (section) {

            if (coord.getX() - 1 >= 0) {
                result.add(new Coordinate(coord.getX() - 1, coord.getY()));
            }
            if (coord.getX() + 1 <= 8) {
                result.add(new Coordinate(coord.getX() + 1, coord.getY()));
            }
            if (coord.getX() - 3 >= 0) {
                result.add(new Coordinate(coord.getX() - 3, coord.getY()));
            }
            if (coord.getX() + 3 <= 8) {
                result.add(new Coordinate(coord.getX() + 3, coord.getY()));
            }

        } else {

            if (coord.getY() - 1 >= 0) {
                result.add(new Coordinate(coord.getX(), coord.getY() - 1));
            }
            if (coord.getY() + 1 <= 8) {
                result.add(new Coordinate(coord.getX(), coord.getY() + 1));
            }
            if (coord.getY() - 3 >= 0) {
                result.add(new Coordinate(coord.getX(), coord.getY() - 3));
            }
            if (coord.getY() + 3 <= 8) {
                result.add(new Coordinate(coord.getX(), coord.getY() + 3));
            }
        }

        result.remove(coord);
        return result;
    }

    public static List<Coordinate> getCoordsAroundHorizontal(Coordinate coord) {

        List<Coordinate> result = new ArrayList<>();

        result.add(new Coordinate(coord.getX() - 1, coord.getY()));
        result.add(new Coordinate(coord.getX() + 1, coord.getY()));


        result.remove(coord);
        return result;
    }

    public static List<Coordinate> getCoordsAroundVertical(Coordinate coord) {

        List<Coordinate> result = new ArrayList<>();

        result.add(new Coordinate(coord.getX(), coord.getY() - 1));
        result.add(new Coordinate(coord.getX(), coord.getY() + 1));


        result.remove(coord);
        return result;
    }

    public static Direction getDirectionOfAngehittet(List<Coordinate> angehittet) {
        if (angehittet.size() <= 1) {
            return Direction.HORIZONTAL;
        }
        return angehittet.get(0).getX() == angehittet.get(1).getX() ? Direction.VERTICAL : Direction.HORIZONTAL;
    }

    private static String getBoardVis(List<List<String>> board) {
        String template =
                "00 01 02 | 10 11 12 | 20 21 22\n" +
                        "03 04 05 | 13 14 15 | 23 24 25\n" +
                        "06 07 08 | 16 17 18 | 26 27 28\n" +
                        "------------------------------\n" +
                        "30 31 32 | 40 41 42 | 50 51 52\n" +
                        "33 34 35 | 43 44 45 | 53 54 55\n" +
                        "36 37 38 | 46 47 48 | 56 57 58\n" +
                        "------------------------------\n" +
                        "60 61 62 | 70 71 72 | 80 81 82\n" +
                        "63 64 65 | 73 74 75 | 83 84 85\n" +
                        "66 67 68 | 76 77 78 | 86 87 88";
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                String val = board.get(i).get(j);
                template = template.replace(i + "" + j, val.length() < 1 ? " " : val);
            }
        }
        return template;
    }

}
