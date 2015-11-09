package controller;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;

import com.sun.xml.bind.v2.TODO;
import database.DatabaseWrapper;
import model.Game;
import model.Gamer;
import model.Score;
import model.User;
import tui.Tui;


/**
 * This class contains all methods that interact between the TUI / API and the data-layer in the Model package of the application.
 *
 * @author Henrik Thorn
 */
public class Logic {
    public static final int GAMES_BY_ID = 0;
    public static final int PENDING_BY_ID = 1;
    public static final int PENDING_INVITED_BY_ID = 2;
    public static final int PENDING_HOSTED_BY_ID = 3;
    public static final int COMPLETED_BY_ID = 4;
    public static final int OPEN_BY_ID = 5;
    public static final int OPEN_GAMES = 6;
    public static final int ALL_GAMES = 7;

    static DatabaseWrapper db = new DatabaseWrapper();
    static Tui tui = new Tui();
    public static boolean adminIsAuthenticated = false;


    public static void serverController(){
        System.out.println("test");
        Scanner input = new Scanner(System.in);
        boolean serverRunning = true;
        while(serverRunning){

            Tui.miscOut("\n***Welcome to the Snake server***\n");
            Tui.miscOut("What do you want to do?");
            Tui.miscOut("1) Login as admin");
            Tui.miscOut("2) Stop server");

            switch (input.nextInt()){
                case 1:
                    tui.login();
                    break;
                case 2:
                    serverRunning = false;
                    break;
                default:
                    Tui.miscOut("Unassigned key.");
            }
        }
    }

    /**
     * Get all users
     *
     * @return ArrayList of users
     */
    public static ArrayList<User> getUsers() {
        return db.getUsers();
    }

    /**
     * Create user
     *
     * @param user
     * @return true if success, false if failure
     */
    public static boolean createUser(User user) {
        user.setPassword(Security.hashing(user.getPassword()));
        if (db.createUser(user))
            return true;
        else {
            return false;
        }
    }

    /**
     * Get specific user
     *
     * @param userId
     * @return User object
     */
    public static User getUser(int userId) {
        return db.getUser(userId);
    }

    /**
     * Delete user
     *
     * @param userId
     * @return rows affected en database
     */
    public static int deleteUser(int userId) {
        return db.deleteEntry(userId, DatabaseWrapper.DELETE_USER);
    }

    /**
     * Authenticates user
     * The method use 2 parameters: username and password which it authenticates as the correct credentials of an existing user.
     * Index 0: User type (0 = admin), (1 = user)
     * Index 1: Error/Succes code (0 = user doesnt exists), (1 = password is wrong), (2 = successful login)
     * Index 2: Contain the authenticated users id
     * @param username
     * @param password
     * @return int[] with user type, error/succes code, userid
     */
    public static int[] authenticateUser(String username, String password) {
        User user;
        int[] result = new int[3];
        user = db.getUserByUsername(username);
        if (user == null) {
            // User does not exists.
            result[1] = 0;
        } else {
            if (password.equals(user.getPassword())) {
                // Return 2 if user exists and password is correct. Success.
                result[0] = user.getType();
                result[1] = 2;
                result[2] = user.getId();

            } else {
                //Return 1 if user exists but password is wrong.
                result[1] = 1;
            }
        }
        return result;
    }


    /**
     * Get specific game
     *
     * @param gameId
     * @return Game object
     */
    public static Game getGame(int gameId) {
        return db.getGame(gameId);
    }

    /**
     * Getting a speific list of games by type and userId
     * @param type
     * @param userId
     * @return ArrayList<Game>
     */
    public static ArrayList<Game> getGames(int type, int userId){
        ArrayList<Game> games = null;

        switch (type){
            case GAMES_BY_ID:
                //Used for showing a user's games
                games = db.getGames(db.GAMES_BY_ID, userId);
                break;
            case PENDING_BY_ID:
                //Used for showing all pending games the user has as host or opponent
                games = db.getGames(db.PENDING_BY_ID, userId);
                break;
            case PENDING_INVITED_BY_ID:
                //Used for showing all pending games the user has been invited to play
                games = db.getGames(db.PENDING_INVITED_BY_ID, userId);
                break;
            case PENDING_HOSTED_BY_ID:
                //Used for showing the open games created by the user
                games = db.getGames(db.PENDING_HOSTED_BY_ID, userId);
                break;
            case OPEN_BY_ID:
                //Used for showing the open games created by the user
                games = db.getGames(db.OPEN_BY_ID, userId);
                break;
            case COMPLETED_BY_ID:
                //Shows all completed games for the user
                games = db.getGames(db.COMPLETED_BY_ID, userId);
                break;
            case OPEN_GAMES:
                //Used for showing all open games, when a user wants to join a game
                //Is getting set to 0 in API class because this method doesn't return games by user ID
                games = db.getGames(db.OPEN_GAMES, userId);
                break;
            case ALL_GAMES:
                //Used for showing all open games, when a user wants to join a game
                //Is getting set to 0 in TUI class because this method doesn't return games by user ID
                games = db.getGames(db.ALL_GAMES, userId);
                break;
        }
        return games;
    }

    /**
     * Create a game
     *
     * @return returns inriched game object
     */
    public static Game createGame(Game game) {

        if (db.createGame(game)) {
            if (game.getOpponent() != null) {
                game.setStatus("pending");
            } else {
                game.setStatus("open");
            }
            return game;
        }
        return null;
    }

    public static boolean joinGame(Game game) {

        if (db.updateGame(game, DatabaseWrapper.JOIN_GAME)==1)
            return true;
        else
            return false;
    }


    /**
     * Starting the game
     * @param requestGame
     * @return Object Game
     */
    public static Game startGame(Game requestGame) {

        //gameid, opponentcontrolls
        Game game = db.getGame(requestGame.getGameId());
        game.getOpponent().setControls(requestGame.getOpponent().getControls());

        Map gamers = GameEngine.playGame(game);

        game = endGame(gamers, game);

        return game;
    }


    //endgame() Called when game is over and pushes score data to the database for future use.
    public static Game endGame(Map gamers, Game game) {

        if(((Gamer)gamers.get('h')).isWinner()){
            game.setWinner(game.getHost());
        }

        else if(((Gamer)gamers.get('o')).isWinner()){
            game.setWinner(game.getOpponent());
        }

        game.setStatus("Finished");
        db.updateGame(game, DatabaseWrapper.FINISH_GAME);
        db.createScore(game);

        return game;
    }

    /**
     * Delete game
     * @param gameId
     * @return rows affected
     */
    public static int deleteGame(int gameId) {
        return db.deleteEntry(gameId, DatabaseWrapper.DELETE_GAME);
    }

    /**
     * Get all highscores from the game
     *
     * @return ArrayList of highscores
     */
    public static ArrayList<Score> getHighscore() {
        //TODO: Get all highscores
        //ArrayList<Score> highScores = db.getHighscore();

        return db.getHighscore();
    }


    public static ArrayList<Score> getScoresByUserID(int userId) {
        return db.getScoresByUserID(userId);
    }
}
