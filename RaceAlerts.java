import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Scanner;

public class RaceAlerts {

    static String ZIPCODE = "10001";
    static int RADIUS_MILES = 15;
    static String RACE_TYPE = "triathlon";
    static int HOURS_BETWEEN_CHECKS = 6;
    static String SEEN_FILE = "seen-races.txt";

    public static void main(String[] args) throws Exception {
        boolean demoMode = false;
        boolean runOnce = false;
        String filter = "";

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--demo")) {
                demoMode = true;
            } else if (args[i].equals("--once")) {
                runOnce = true;
            } else if (args[i].startsWith("--filter=")) {
                filter = args[i].substring(9).toLowerCase();
            }
        }

        if (demoMode) {
            checkForNewRaces(getSampleJson(), filter);
            return;
        }

        if (runOnce) {
            checkForNewRaces(downloadRaces(), filter);
            return;
        }

        System.out.println("Watching for new NYC triathlons every " + HOURS_BETWEEN_CHECKS + " hours. Press Ctrl+C to stop.");
        while (true) {
            try {
                checkForNewRaces(downloadRaces(), filter);
            } catch (Exception e) {
                System.out.println("Check failed: " + e.getMessage());
            }
            long waitTime = HOURS_BETWEEN_CHECKS * 60L * 60L * 1000L;
            Thread.sleep(waitTime);
        }
    }

    static String downloadRaces() throws Exception {
        String today = LocalDate.now().toString();

        String link = "https://runsignup.com/Rest/races?format=json&events=T"
                + "&zipcode=" + ZIPCODE
                + "&radius=" + RADIUS_MILES
                + "&start_date=" + today
                + "&name=" + RACE_TYPE
                + "&results_per_page=100&sort=date+ASC";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(link))
                .header("User-Agent", "NYC-Race-Alerts/1.0")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("RunSignup returned error code " + response.statusCode());
        }
        return response.body();
    }

    static void checkForNewRaces(String json, String filter) throws IOException {
        ArrayList<Race> allRaces = readRacesFromJson(json);
        ArrayList<String> seenIds = loadSeenIds();
        ArrayList<Race> matchingRaces = new ArrayList<Race>();
        ArrayList<Race> newRaces = new ArrayList<Race>();

        for (Race race : allRaces) {
            if (!filter.equals("") && !matchesFilter(race, filter)) {
                continue;
            }
            matchingRaces.add(race);
            if (!seenIds.contains(race.id)) {
                newRaces.add(race);
                seenIds.add(race.id);
            }
        }
        saveSeenIds(seenIds);

        String line = "========================================";

        System.out.println();
        System.out.println(line);
        System.out.println("Found " + matchingRaces.size() + " races, " + newRaces.size() + " new.");
        System.out.println(line);

        for (Race race : matchingRaces) {
            System.out.println();
            if (newRaces.contains(race)) {
                System.out.println("🆕 NEW");
            }
            System.out.print(describeRace(race));
            System.out.println("----------------------------------------");
        }

        if (newRaces.size() == 0) {
            return;
        }

        String message = "🏊🚴🏃 **New triathlons near NYC**\n";
        for (Race race : newRaces) {
            message += "\n" + describeRace(race);
        }
        sendToDiscord(message);
    }

    static String describeRace(Race race) {
        String distances = race.distances;
        if (distances.equals("")) {
            distances = "not listed";
        }

        String registration = "closed";
        if (race.registrationOpen) {
            registration = "open ✅";
        }

        String text = "";
        text += "🏁 " + race.name + "\n";
        text += "   Date:         " + race.date + "\n";
        text += "   City:         " + race.city + "\n";
        text += "   Distances:    " + distances + "\n";
        text += "   Registration: " + registration + "\n";
        text += "   Link:         " + race.url + "\n";
        return text;
    }

    static boolean matchesFilter(Race race, String filter) {
        String text = (race.name + " " + race.distances).toLowerCase();

        if (filter.equals("half")) {
            return text.contains("half") || text.contains("13.1");
        } else if (filter.equals("marathon")) {
            boolean fullMarathon = text.contains("marathon") && !text.contains("half");
            return fullMarathon || text.contains("26.2");
        } else {
            return text.contains(filter);
        }
    }

    static ArrayList<Race> readRacesFromJson(String json) {
        ArrayList<Race> races = new ArrayList<Race>();
        String[] pieces = json.split("\\{\"race\":");

        for (int i = 1; i < pieces.length; i++) {
            String piece = pieces[i];

            Race race = new Race();
            race.id = getValue(piece, "race_id");
            race.name = getValue(piece, "name");
            race.date = getValue(piece, "next_date");
            race.city = getValue(piece, "city");
            race.url = getValue(piece, "url");
            race.registrationOpen = getValue(piece, "is_registration_open").equals("T");
            race.distances = getAllDistances(piece);

            if (race.date.equals("") || race.date.equals("null")) {
                race.date = "TBD";
            }
            races.add(race);
        }
        return races;
    }

    static String getValue(String text, String key) {
        String searchFor = "\"" + key + "\":";
        int start = text.indexOf(searchFor);
        if (start == -1) {
            return "";
        }
        start = start + searchFor.length();

        if (text.charAt(start) == '"') {
            int end = start + 1;
            while (text.charAt(end) != '"' || text.charAt(end - 1) == '\\') {
                end++;
            }
            String value = text.substring(start + 1, end);
            value = value.replace("\\/", "/");
            value = value.replace("\\\"", "\"");
            return value;
        } else {
            int end = start;
            while (text.charAt(end) != ',' && text.charAt(end) != '}') {
                end++;
            }
            return text.substring(start, end).trim();
        }
    }

    static String getAllDistances(String text) {
        String distances = "";
        String searchFor = "\"distance\":";
        int position = text.indexOf(searchFor);

        while (position != -1) {
            String value = getValue(text.substring(position), "distance");
            if (!value.equals("") && !value.equals("null")) {
                if (!distances.equals("")) {
                    distances += ", ";
                }
                distances += value;
            }
            position = text.indexOf(searchFor, position + 1);
        }
        return distances;
    }

    static String getConfiguredWebhook() {
        String value = System.getenv("DISCORD_WEBHOOK_URL");
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }

        value = System.getProperty("DISCORD_WEBHOOK_URL");
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }

        File envFile = new File(".env");
        if (!envFile.exists()) {
            return "";
        }

        try (Scanner scanner = new Scanner(envFile)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int equalsIndex = line.indexOf('=');
                if (equalsIndex == -1) {
                    continue;
                }
                String key = line.substring(0, equalsIndex).trim();
                String val = line.substring(equalsIndex + 1).trim();
                if (key.equals("DISCORD_WEBHOOK_URL")) {
                    return val.replace("\"", "").replace("'", "");
                }
            }
        } catch (IOException e) {
            System.out.println("Could not read .env file: " + e.getMessage());
        }

        return "";
    }

    static void sendToDiscord(String message) {
        String webhook = getConfiguredWebhook();
        if (webhook == null || webhook.trim().isEmpty()) {
            System.out.println("No Discord webhook configured in DISCORD_WEBHOOK_URL or .env, so nothing was sent to Discord.");
            return;
        }

        HttpClient client = HttpClient.newHttpClient();

        for (int i = 0; i < message.length(); i += 1900) {
            int end = Math.min(i + 1900, message.length());
            String piece = message.substring(i, end);

            piece = piece.replace("\\", "\\\\");
            piece = piece.replace("\"", "\\\"");
            piece = piece.replace("\n", "\\n");
            String body = "{\"content\":\"" + piece + "\"}";

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhook))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 || response.statusCode() == 204) {
                    System.out.println("Sent to Discord ✅");
                } else {
                    System.out.println("Discord said no (" + response.statusCode() + "): " + response.body());
                }
            } catch (Exception e) {
                System.out.println("Discord alert failed: " + e.getMessage());
            }
        }
    }

    static ArrayList<String> loadSeenIds() throws IOException {
        ArrayList<String> ids = new ArrayList<String>();
        File file = new File(SEEN_FILE);
        if (!file.exists()) {
            return ids;
        }

        Scanner reader = new Scanner(file);
        while (reader.hasNextLine()) {
            String id = reader.nextLine();
            if (id.endsWith(".0")) {
                id = id.substring(0, id.length() - 2);
            }
            ids.add(id);
        }
        reader.close();
        return ids;
    }

    static void saveSeenIds(ArrayList<String> ids) throws IOException {
        FileWriter writer = new FileWriter(SEEN_FILE);
        for (String id : ids) {
            writer.write(id + "\n");
        }
        writer.close();
    }

    static String getSampleJson() {
        return """
            {"races":[
              {"race":{"race_id":2001,"name":"Big Apple Triathlon","next_date":"07/18/2027",
                "is_registration_open":"T","url":"https://example.com/big-apple-tri",
                "address":{"city":"New York","state":"NY"},
                "events":[{"name":"Sprint Triathlon","distance":"16.2 Miles"}]}},

              {"race":{"race_id":2002,"name":"Atlantic Triathlon","next_date":"08/08/2027",
                "is_registration_open":"T","url":"https://example.com/atlantic-tri",
                "address":{"city":"Brooklyn","state":"NY"},
                "events":[{"name":"Olympic","distance":"32 Miles"},{"name":"Sprint","distance":"16 Miles"}]}},

              {"race":{"race_id":2003,"name":"Staten Island Triathlon","next_date":"09/12/2027",
                "is_registration_open":"F","url":"https://example.com/staten-island-tri",
                "address":{"city":"Staten Island","state":"NY"},
                "events":[{"name":"Sprint","distance":"15.5 Miles"}]}}
            ]}
            """;
    }
}

class Race {
    String id;
    String name;
    String date;
    String city;
    String url;
    boolean registrationOpen;
    String distances;
}
