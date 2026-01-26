(ns muusikoidennet-bot.core
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [net.cgrand.enlive-html :as html])
  (:gen-class))

(def search-url "https://muusikoiden.net/tori/haku.php")
(def browse-url "https://muusikoiden.net/tori/")
(def searches-file "data/searches.json")
(def data-file "data/seen-items.json")

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
        (->> (filter :enabled)))
    []))

(defn build-search-url [{:keys [params]}]
  (let [base (if (:keyword params) search-url browse-url)
        query-string (->> params
                          (map (fn [[k v]] (str (name k) "=" (java.net.URLEncoder/encode (str v) "UTF-8"))))
                          (clojure.string/join "&"))]
    (str base "?" query-string)))

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
  (-> (http/get url {:headers {"User-Agent" "Mozilla/5.0 (compatible; MuusikoidenBot/1.0)"}})
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

(defn fetch-search-listings [search]
  (let [url (build-search-url search)
        _ (println (str "\n--- " (:name search) " ---"))
        _ (println (str "URL: " url))
        page (fetch-page url)
        listings (extract-listings page)]
    (println (str "Found " (count listings) " listings"))
    listings))

(defn dedupe-listings [listings]
  (->> listings
       (group-by :id)
       vals
       (map first)))

(defn -main [& args]
  (println "Fetching muusikoiden.net listings...")

  (try
    (let [searches (load-searches)
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
            (print-new-listing listing)))
        (println "\nNo new listings found."))

      (save-seen-items updated-seen-ids)
      (println (str "\nTotal tracked items: " (count updated-seen-ids))))

    (catch Exception e
      (println (str "Error: " (.getMessage e)))
      (System/exit 1))))
