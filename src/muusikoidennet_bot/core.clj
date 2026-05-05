(ns muusikoidennet-bot.core
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [net.cgrand.enlive-html :as html])
  (:gen-class))

(def searches-file "data/searches.json")
(def data-file "data/seen-items.json")
(def config-file "data/config.json")

;; =============================================================================
;; Config & Telegram
;; =============================================================================

(defn load-config []
  (let [file-config (if (.exists (io/file config-file))
                      (-> (slurp config-file)
                          (json/read-str :key-fn keyword))
                      {})
        ;; Override with environment variables if set
        env-api-key (System/getenv "TELEGRAM_API_KEY")
        env-chat-id (System/getenv "TELEGRAM_CHAT_ID")
        env-enabled (System/getenv "TELEGRAM_ENABLED")]
    (cond-> file-config
      env-api-key (assoc-in [:telegram :api_key] env-api-key)
      env-chat-id (assoc-in [:telegram :chat_id] env-chat-id)
      env-enabled (assoc-in [:telegram :enabled] (= env-enabled "true")))))

(defn telegram-send-message [config text]
  (let [api-key (get-in config [:telegram :api_key])
        chat-id (get-in config [:telegram :chat_id])
        url (str "https://api.telegram.org/bot" api-key "/sendMessage")]
    (try
      (http/post url
                 {:form-params {:chat_id chat-id
                                :text text
                                :parse_mode "HTML"
                                :disable_web_page_preview "false"}})
      (catch Exception e
        (println (str "Telegram error: " (.getMessage e)))))))

(defn telegram-enabled? [config]
  (and (get-in config [:telegram :enabled])
       (not= (get-in config [:telegram :api_key]) "YOUR_BOT_API_KEY_HERE")))

(defn format-listing-telegram [{:keys [title url]}]
  (str "<b>" title "</b>\n" url))

(defn notify-new-listings [config listings]
  (when (and (telegram-enabled? config) (seq listings))
    (let [header (str "🎸 " (count listings) " new listing(s) found!\n\n")
          body (clojure.string/join "\n\n" (map format-listing-telegram listings))
          message (str header body)]
      (telegram-send-message config message))))

;; =============================================================================
;; Telegram Commands
;; =============================================================================

(defn telegram-get-updates [config last-update-id]
  (let [api-key (get-in config [:telegram :api_key])
        url (str "https://api.telegram.org/bot" api-key "/getUpdates")]
    (try
      (let [response (http/get url
                               {:query-params (cond-> {:timeout 0}
                                                last-update-id (assoc :offset (inc last-update-id)))})
            body (json/read-str (:body response))]
        (get body "result"))
      (catch Exception e
        (println (str "Telegram getUpdates error: " (.getMessage e)))
        []))))

(defn save-config [config]
  (spit config-file (json/write-str config :indent true)))

(defn save-searches [searches]
  (spit searches-file (json/write-str {:searches searches} :indent true)))

(defn load-searches-raw []
  (if (.exists (io/file searches-file))
    (-> (slurp searches-file)
        (json/read-str :key-fn keyword)
        :searches)
    []))

(defn valid-search-url? [url]
  (and (string? url)
       (clojure.string/starts-with? url "https://muusikoiden.net/tori")))

(defn handle-add-command [config text]
  (let [url (clojure.string/trim (subs text 4))]
    (cond
      (clojure.string/blank? url)
      (telegram-send-message config
                             "Usage: /add <url>\n\nExample:\n/add https://muusikoiden.net/tori/haku.php?keyword=strandberg")

      (not (valid-search-url? url))
      (telegram-send-message config "Invalid URL. Must start with https://muusikoiden.net/tori")

      :else
      (let [searches (load-searches-raw)]
        (if (some #{url} searches)
          (telegram-send-message config "This search already exists.")
          (do
            (save-searches (conj (vec searches) url))
            (telegram-send-message config (str "✅ Search added:\n" url))))))))

(defn handle-list-command [config]
  (let [searches (load-searches-raw)]
    (if (seq searches)
      (let [lines (map-indexed
                   (fn [i url] (str (inc i) ". " url))
                   searches)]
        (telegram-send-message config
                               (str "📋 Searches:\n\n"
                                    (clojure.string/join "\n\n" lines))))
      (telegram-send-message config "No searches configured."))))

(defn handle-remove-command [config text]
  (let [arg (clojure.string/trim (subs text 7))
        searches (vec (load-searches-raw))
        idx (try (dec (Integer/parseInt arg)) (catch Exception _ nil))]
    (cond
      (clojure.string/blank? arg)
      (telegram-send-message config "Usage: /remove <number>")

      (nil? idx)
      (telegram-send-message config "Please use a number. Use /list to see searches.")

      (or (neg? idx) (>= idx (count searches)))
      (telegram-send-message config (str "Invalid number (1-" (count searches) ")"))

      :else
      (let [removed (nth searches idx)
            updated (vec (concat (take idx searches) (drop (inc idx) searches)))]
        (save-searches updated)
        (telegram-send-message config (str "🗑 Removed:\n" removed))))))

(defn handle-help-command [config]
  (telegram-send-message config
                         (str "🎸 <b>Muusikoiden.net Bot</b>\n\n"
                              "Commands:\n"
                              "/add &lt;url&gt; - Add search URL\n"
                              "/list - List all searches\n"
                              "/remove &lt;n&gt; - Remove search by number\n"
                              "/help - Show this help\n\n"
                              "Example:\n"
                              "<code>/add https://muusikoiden.net/tori/haku.php?keyword=strandberg</code>")))

(defn process-telegram-command [config message]
  (let [text (get message "text" "")
        chat-id (str (get-in message ["chat" "id"]))]
    ;; Only process from configured chat
    (when (= chat-id (get-in config [:telegram :chat_id]))
      (cond
        (clojure.string/starts-with? text "/add") (handle-add-command config text)
        (clojure.string/starts-with? text "/list") (handle-list-command config)
        (clojure.string/starts-with? text "/remove") (handle-remove-command config text)
        (clojure.string/starts-with? text "/help") (handle-help-command config)
        (clojure.string/starts-with? text "/start") (handle-help-command config)))))

(defn process-telegram-updates [config]
  (when (telegram-enabled? config)
    (let [last-update-id (get-in config [:telegram :last_update_id])
          updates (telegram-get-updates config last-update-id)]
      (when (seq updates)
        (println (str "Processing " (count updates) " Telegram update(s)..."))
        (doseq [update updates]
          (when-let [message (get update "message")]
            (process-telegram-command config message)))
        ;; Save last update ID
        (let [max-update-id (apply max (map #(get % "update_id") updates))
              updated-config (assoc-in config [:telegram :last_update_id] max-update-id)]
          (save-config updated-config))))))

;; =============================================================================
;; Search Parameter Reference
;; =============================================================================

(def param-reference
  {:keyword {:description "Search keywords"
             :type :text}

   :title_only {:description "Where to search"
                :values {"0" "Koko ilmoituksista (Full listings)"
                         "1" "Vain otsikoista (Titles only)"}}

   :location {:description "Lääni (Province)"
              :values {"all"                 "Kaikki ilmoitukset"
                       "suomi"               "Koko Suomi"
                       "other"               "Ulkomaat"
                       "Ahvenanmaa"          "Ahvenanmaa"
                       "Etelä-Suomen lääni"  "Etelä-Suomen lääni"
                       "Itä-Suomen lääni"    "Itä-Suomen lääni"
                       "Lapin lääni"         "Lapin lääni"
                       "Länsi-Suomen lääni"  "Länsi-Suomen lääni"
                       "Oulun lääni"         "Oulun lääni"}}

   :province {:description "Maakunta (Region)"
              :values {"Ahvenanmaa"       "Ahvenanmaa"
                       "Etelä-Karjala"    "Etelä-Karjala"
                       "Etelä-Pohjanmaa"  "Etelä-Pohjanmaa"
                       "Etelä-Savo"       "Etelä-Savo"
                       "Kainuu"           "Kainuu"
                       "Kanta-Häme"       "Kanta-Häme"
                       "Keski-Pohjanmaa"  "Keski-Pohjanmaa"
                       "Keski-Suomi"      "Keski-Suomi"
                       "Kymenlaakso"      "Kymenlaakso"
                       "Lappi"            "Lappi"
                       "Pirkanmaa"        "Pirkanmaa"
                       "Pohjanmaa"        "Pohjanmaa"
                       "Pohjois-Karjala"  "Pohjois-Karjala"
                       "Pohjois-Pohjanmaa" "Pohjois-Pohjanmaa"
                       "Pohjois-Savo"     "Pohjois-Savo"
                       "Päijät-Häme"      "Päijät-Häme"
                       "Satakunta"        "Satakunta"
                       "Uusimaa"          "Uusimaa"
                       "Varsinais-Suomi"  "Varsinais-Suomi"}}

   :city {:description "Paikkakunta (City) - numeric codes"
          :examples {"049" "Espoo"
                     "091" "Helsinki"
                     "837" "Tampere"
                     "853" "Turku"
                     "564" "Oulu"
                     "179" "Jyväskylä"
                     "297" "Kuopio"
                     "398" "Lahti"
                     "609" "Pori"
                     "167" "Joensuu"
                     "905" "Vaasa"
                     "743" "Seinäjoki"
                     "698" "Rovaniemi"
                     "491" "Mikkeli"
                     "740" "Savonlinna"
                     "205" "Kajaani"}}

   :type {:description "Ilmoitustyyppi (Listing type)"
          :values {"all"       "Kaikki"
                   "sell"      "Myydään"
                   "buy"       "Ostetaan"
                   "exchange"  "Vaihdetaan"
                   "for_rent"  "Vuokrataan"
                   "want_rent" "Halutaan vuokrata"
                   "other"     "Muut"}}

   :category {:description "Osasto (Category)"
              :values {"all" "Kaikki"
                       ;; Akustiset kitarat
                       "1"  "Akustiset kitarat"
                       "2"  "  Vasurit"
                       "3"  "  12-kieliset"
                       "4"  "  Teräskieliset"
                       "5"  "  Nailonkieliset"
                       "6"  "  Elektroakustiset"
                       "7"  "  Muut"
                       ;; Sähkökitarat
                       "8"  "Sähkökitarat"
                       "9"  "  Vasurit"
                       "10" "  7+-kieliset"
                       "11" "  Baritonit"
                       "12" "  Muut"
                       ;; Bassot
                       "13" "Bassot"
                       "14" "  Vasurit"
                       "15" "  Akustiset"
                       "16" "  5+-kieliset"
                       "17" "  Muut"
                       ;; Rummut
                       "18" "Rummut"
                       "19" "  Sähkörummut"
                       "20" "  Setit"
                       "21" "  Virvelit"
                       "22" "  Irtorummut"
                       "23" "  Symbaalit"
                       "24" "  Kalvot"
                       "25" "  Pedaalit"
                       "26" "  Telineet"
                       "27" "  Muut"
                       "35" "  Perkussiot"
                       ;; Kosketinsoittimet
                       "28"  "Kosketinsoittimet"
                       "29"  "  Akustiset pianot"
                       "30"  "  Sähköpianot"
                       "31"  "  Syntetisaattorit"
                       "122" "  Samplerit, modulit"
                       "32"  "  Muut"
                       ;; Muut instrumentit
                       "33" "Muut instrumentit"
                       "34" "  Muut kielisoittimet"
                       "36" "  Jousisoittimet"
                       "37" "  Puhaltimet"
                       "38" "  Harmonikat"
                       "39" "  Muut"
                       ;; Kitaravahvistimet
                       "40" "Kitaravahvistimet ja -kaapit"
                       "41" "  Kombot"
                       "42" "  Nupit"
                       "43" "  Kaapit"
                       "44" "  Muut"
                       ;; Bassovahvistimet
                       "45" "Bassovahvistimet ja -kaapit"
                       "46" "  Kombot"
                       "47" "  Nupit"
                       "48" "  Kaapit"
                       "49" "  Muut"
                       ;; Varaosat
                       "87"  "Kitara- ja bassovaraosat"
                       "52"  "  Kitaramikrofonit"
                       "53"  "  Bassomikrofonit"
                       "124" "  Rungot ja kaulat"
                       "90"  "  Tallat"
                       "125" "  Virittimet"
                       "126" "  Plektrasuojat"
                       "91"  "  Kaapelit"
                       "92"  "  Muut"
                       ;; Efektit
                       "55" "Efektit, pedaalit"
                       "56" "  Kitara"
                       "57" "  Basso"
                       "58" "  Tarvikkeet"
                       "59" "  Muut"
                       ;; PA
                       "60" "PA-laitteet"
                       "61" "  Kaiuttimet"
                       "62" "  Mikserit"
                       "63" "  Vahvistimet"
                       "64" "  Tarvikkeet"
                       "65" "  Muut"
                       ;; Mikrofonit
                       "50"  "Mikrofonit"
                       "51"  "  Dynaamiset mikrofonit"
                       "123" "  Kondensaattorimikrofonit"
                       "54"  "  Muut"
                       ;; Studio/PC
                       "66" "Studio/PC"
                       "67" "  Monitorit, kaiuttimet"
                       "68" "  Mikserit, raiturit"
                       "69" "  Vahvistimet"
                       "70" "  Kuulokkeet"
                       "71" "  Digitaalitallentimet"
                       "72" "  Tietokoneet"
                       "73" "  Äänikortit"
                       "74" "  Ohjelmistot"
                       "75" "  Muut"
                       ;; DJ
                       "76" "DJ-kamat"
                       "77" "  Soittimet"
                       "78" "  Mikserit"
                       "79" "  Kontrollerit"
                       "80" "  Muut"
                       ;; Kotelot
                       "81" "Kotelot ja telineet"
                       "82" "  Soitinkotelot"
                       "83" "  Laiteräkit ja tarvikkeet"
                       "84" "  Kuljetuslaatikot"
                       "85" "  Jalustat, telineet"
                       "86" "  Muut"
                       ;; Valo
                       "93" "Valo- ja showtekniikka"
                       "94" "  Valot"
                       "95" "  Ohjaimet/pöydät"
                       "96" "  Tarvikkeet"
                       "97" "  Muut"
                       ;; Hifi
                       "98"  "Hifi"
                       "99"  "  Soittimet"
                       "100" "  Vahvistimet"
                       "101" "  Kaiuttimet"
                       "102" "  Muut"
                       ;; Äänitteet
                       "103" "Äänitteet"
                       "104" "  CD"
                       "105" "  Vinyylit"
                       "106" "  DVD / Bluray"
                       "107" "  Muut"
                       ;; Kirjallisuus
                       "108" "Kirjallisuus"
                       "109" "  Nuotit"
                       "110" "  Tabulatuurit"
                       "111" "  Muut"
                       ;; Palvelut
                       "112" "Äänitys- ja livepalvelut"
                       "113" "  Kaupalliset"
                       "114" "  Harrasteet"
                       "115" "  Muut"
                       ;; Treenikämpät
                       "116" "Treenikämpät"
                       "117" "  Vuokrataan"
                       "118" "  Halutaan vuokrata"
                       "119" "  Muut"
                       ;; Muut
                       "120" "Muille osastoille sopimattomat"
                       "121" "  Muille osastoille sopimattomat"}}

   :with_image {:description "Kuvat (Images)"
                :values {"0" "Kaikki ilmoitukset"
                         "1" "Vain kuvalliset ilmoitukset"}}

   :price_min {:description "Myyntihinta vähintään (Min price in €)"
               :type :number}

   :price_max {:description "Myyntihinta enintään (Max price in €)"
               :type :number}

   :sort {:description "Tulokset (Sort order)"
          :values {"match"  "Parhaiten vastaava ilmoitus ensin"
                   "new"    "Uusin ilmoitus ensin"
                   "pricea" "Pienin myyntihinta ensin"
                   "priced" "Suurin myyntihinta ensin"}}})

(defn load-searches []
  (if (.exists (io/file searches-file))
    (-> (slurp searches-file)
        (json/read-str :key-fn keyword)
        :searches
        vec)
    []))

(defn ensure-data-dir []
  (let [dir (io/file "data")]
    (when-not (.exists dir)
      (.mkdirs dir))))

(defn load-seen-items []
  (ensure-data-dir)
  (if (.exists (io/file data-file))
    (-> (slurp data-file)
        (json/read-str :key-fn keyword)
        :seen-ids
        set)
    #{}))

(defn save-seen-items [seen-ids]
  (ensure-data-dir)
  (spit data-file
        (json/write-str {:seen-ids (vec seen-ids)
                         :updated-at (str (java.time.Instant/now))}
                        :indent true)))

(defn fetch-page [url]
  (-> (http/get url {:headers {"User-Agent" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"}})
      :body
      (html/html-snippet)))

(defn extract-listing-id [href]
  (when href
    (second (re-find #"/tori/ilmoitus/(\d+)" href))))

(defn parse-listing [listing-node]
  (let [link (first (html/select listing-node [:a]))
        href (get-in link [:attrs :href])
        id (extract-listing-id href)
        title (html/text link)
        ;; Find price - look for text containing €
        all-text (html/text listing-node)
        price (second (re-find #"(\d+[\s\d]*€)" all-text))
        ;; Extract location
        location (second (re-find #"Paikkakunta:\s*([^\n]+)" all-text))]
    (when id
      {:id id
       :title (clojure.string/trim (or title ""))
       :price (when price (clojure.string/trim price))
       :location (when location (clojure.string/trim location))
       :url (str "https://muusikoiden.net" href)})))

(defn extract-listings [page]
  (let [;; Listings are in divs/sections that contain links to /tori/ilmoitus/
        links (html/select page [[:a (html/attr-contains :href "/tori/ilmoitus/")]])
        ;; Get parent containers for more context
        listing-data (for [link links
                          :let [href (get-in link [:attrs :href])
                                id (extract-listing-id href)
                                title (html/text link)]
                          :when id]
                      {:id id
                       :title (clojure.string/trim (or title ""))
                       :url (str "https://muusikoiden.net" href)})]
    ;; Remove duplicates by ID
    (->> listing-data
         (group-by :id)
         vals
         (map first))))

(defn print-new-listing [{:keys [id title url price location]}]
  (println "")
  (println "🎸 NEW LISTING:")
  (println (str "   Title: " title))
  (when price (println (str "   Price: " price)))
  (when location (println (str "   Location: " location)))
  (println (str "   URL: " url))
  (println (str "   ID: " id)))

(defn fetch-search-listings [url]
  (println (str "\n--- " url " ---"))
  (let [page (fetch-page url)
        listings (extract-listings page)]
    (println (str "Found " (count listings) " listings"))
    listings))

(defn dedupe-listings [listings]
  (->> listings
       (group-by :id)
       vals
       (map first)))

(defn -main [& args]
  (println "Muusikoiden.net Bot starting...")

  (try
    (let [config (load-config)
          _ (println (str "Telegram: " (if (telegram-enabled? config) "enabled" "disabled")))
          ;; Process any pending Telegram commands first
          _ (process-telegram-updates config)
          ;; Reload searches in case they were modified by commands
          searches (load-searches)
          _ (println (str "Loaded " (count searches) " search(es)"))
          seen-ids (load-seen-items)
          _ (println (str "Previously seen items: " (count seen-ids)))
          ;; Collect all listings from all searches
          all-listings (mapcat fetch-search-listings searches)
          ;; Deduplicate by ID
          unique-listings (dedupe-listings all-listings)
          _ (println (str "\n=== RESULTS ==="))
          _ (println (str "Total listings found: " (count all-listings)))
          _ (println (str "Unique listings: " (count unique-listings)))
          ;; Filter out already seen
          new-listings (vec (remove #(seen-ids (:id %)) unique-listings))
          new-ids (map :id new-listings)
          updated-seen-ids (into seen-ids new-ids)]

      (if (seq new-listings)
        (do
          (println (str "\n✨ " (count new-listings) " NEW LISTING(S):"))
          (doseq [listing new-listings]
            (print-new-listing listing))
          ;; Send Telegram notification
          (notify-new-listings config new-listings))
        (println "\nNo new listings found."))

      (save-seen-items updated-seen-ids)
      (println (str "\nTotal tracked items: " (count updated-seen-ids))))

    (catch Exception e
      (println (str "Error: " (.getMessage e)))
      (System/exit 1))))
