(ns oc.web.actions.org
  (:require-macros [if-let.core :refer (when-let*)])
  (:require [taoensso.timbre :as timbre]
            [oc.web.api :as api]
            [oc.web.lib.jwt :as jwt]
            [oc.web.urls :as oc-urls]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.actions.comment :as ca]
            [oc.web.actions.reaction :as ra]
            [oc.web.actions.activity :as aa]
            [oc.web.actions.section :as sa]
            [oc.web.lib.json :refer (json->cljs)]
            [oc.web.lib.ws-change-client :as ws-cc]
            [oc.web.lib.ws-interaction-client :as ws-ic]
            [oc.web.actions.notifications :as notification-actions]))

;; User related functions
;; FIXME: these functions shouldn't be here but calling oc.web.actions.user from here is causing a circular dep

(defn bot-auth [team-data user-data & [redirect-to]]
  (let [redirect (or redirect-to (router/get-token))
        auth-link (utils/link-for (:links team-data) "bot")
        fixed-auth-url (utils/slack-link-with-state (:href auth-link) (:user-id user-data) (:team-id team-data)
                        redirect)]
    (router/redirect! fixed-auth-url)))

(defn maybe-show-bot-added-notification? []
  ;; Do we need to show the add bot banner?
  (when-let* [org-data (dis/org-data)
              bot-access (dis/bot-access)]
    (when (= bot-access :slack-bot-success-notification)
      (notification-actions/show-notification {:title "Slack integration successful"
                                               :slack-icon true
                                               :id "slack-bot-integration-succesful"}))
    (dis/dispatch! [:input [:bot-access] nil])))

;; Org get
(defn check-org-404 []
  (let [orgs (dis/orgs-data)]
    ;; avoid infinite loop of the Go to digest button
    ;; by changing the value of the last visited slug
    (if (pos? (count orgs))
      (cook/set-cookie! (router/last-org-cookie) (:slug (first orgs)) (* 60 60 24 6))
      (cook/remove-cookie! (router/last-org-cookie)))
    (router/redirect-404!)))

(defn org-loaded [org-data saved?]
  ;; Save the last visited org
  (when (and org-data
             (= (router/current-org-slug) (:slug org-data)))
    (cook/set-cookie! (router/last-org-cookie) (:slug org-data) (* 60 60 24 6)))
  ;; Check the loaded org
  (let [ap-initial-at (:ap-initial-at @dis/app-state)
        boards (:boards org-data)
        activity-link (utils/link-for (:links org-data) "activity")]
    (sa/load-other-sections (:boards org-data))
    (when activity-link
      ;; Preload all posts data
      (aa/all-posts-get org-data ap-initial-at)
      ;; Preload must see data
      (aa/must-see-get org-data))
    (cond
      ;; If it's all posts page or must see, loads AP and must see for the current org
      (or (= (router/current-board-slug) "all-posts")
          ap-initial-at
          (= (router/current-board-slug) "must-see"))
      (when-not activity-link
        (check-org-404))

      ; If there is a board slug let's load the board data
      (router/current-board-slug)
      (if-let [board-data (first (filter #(= (:slug %) (router/current-board-slug)) boards))]
        ; Load the board data since there is a link to the board in the org data
        (when-let [board-link (utils/link-for (:links board-data) ["item" "self"] "GET")]
          (sa/section-get board-link))
        ; The board wasn't found, showing a 404 page
        (if (= (router/current-board-slug) utils/default-drafts-board-slug)
          (utils/after 100 #(sa/section-get-finish utils/default-drafts-board))
          (when-not (router/current-activity-id) ;; user is not asking for a specific post
            (router/redirect-404!))))
      ;; Board redirect handles
      (and (not (utils/in? (:route @router/path) "org-settings-invite"))
           (not (utils/in? (:route @router/path) "org-settings-team"))
           (not (utils/in? (:route @router/path) "org-settings"))
           (not (utils/in? (:route @router/path) "email-verification"))
           (not (utils/in? (:route @router/path) "sign-up"))
           (not (utils/in? (:route @router/path) "email-wall"))
           (not (utils/in? (:route @router/path) "confirm-invitation"))
           (not (utils/in? (:route @router/path) "secure-activity")))
      ;; Redirect to the first board if at least one is present
      (let [board-to (utils/get-default-board org-data)]
        (router/nav!
          (if board-to
            (oc-urls/board (:slug org-data) (:slug board-to))
            (oc-urls/all-posts (:slug org-data)))))))
  ;; Change service connection
  (when (jwt/jwt) ; only for logged in users
    (when-let [ws-link (utils/link-for (:links org-data) "changes")]
      (ws-cc/reconnect ws-link (jwt/get-key :user-id) (:slug org-data) (conj (map :uuid (:boards org-data)) (:uuid org-data)))))

  ;; Interaction service connection
  (when (jwt/jwt) ; only for logged in users
    (when-let [ws-link (utils/link-for (:links org-data) "interactions")]
      (ws-ic/reconnect ws-link (jwt/get-key :user-id))))
  (dis/dispatch! [:org-loaded org-data saved?])
  (utils/after 100 maybe-show-bot-added-notification?))

(defn get-org-cb [{:keys [status body success]}]
  (let [org-data (json->cljs body)]
    (org-loaded org-data false)))

(defn get-org [& [org-data]]
  (let [fixed-org-data (or org-data (dis/org-data))]
    (api/get-org fixed-org-data get-org-cb)))

;; Org redirect

(defn org-redirect [org-data]
  (when org-data
    (let [org-slug (:slug org-data)]
      (utils/after 100 #(router/redirect! (oc-urls/all-posts org-slug))))))

;; Org create

(defn- org-created [org-data]
  (let [auth-source (jwt/get-key :auth-source)]
    (if (= auth-source "email")
      (router/nav! (oc-urls/sign-up-invite (:slug org-data)))
      (org-redirect org-data))))

(defn team-patch-cb [org-data {:keys [success body status]}]
  (when success
    (org-created org-data)))

(defn- handle-org-redirect [team-data org-data]
  (if (and (empty? (:name team-data))
           (utils/link-for (:links team-data) "partial-update"))
    ;; if the current team has no name and
    ;; the user has write permission on it
    ;; use the org name
    ;; for it and patch it back
    (api/patch-team (:team-id org-data) {:name (:name org-data)} org-data (partial team-patch-cb org-data))
    ;; if not redirect the user to the invite page
    (org-created org-data)))

(defn org-create-cb [{:keys [success status body]} email-domains]
  (if success
    (when-let [org-data (when success (json->cljs body))]
      (org-loaded org-data false)
      (let [team-data (dis/team-data (:team-id org-data))]
        (if (pos? (count email-domains))
          (doseq [domain email-domains]
            (api/add-email-domain domain
                                  #(handle-org-redirect team-data org-data)
                                  team-data))
          (handle-org-redirect team-data org-data))))
    (when (= status 409)
      ;; Redirect to the already available org
      (router/nav! (oc-urls/org (:slug (first (dis/orgs-data))))))))

(defn org-create [org-data]
  (when (seq (:name org-data))
    (let [email-domains (remove nil? [(:email-domain org-data)])]
      (api/create-org (:name org-data)
                      (:logo-url org-data)
                      (:logo-width org-data)
                      (:logo-height org-data)
                      email-domains
                      org-create-cb))))

;; Org edit

(defn org-edit-setup [org-data]
  (dis/dispatch! [:org-edit-setup org-data]))

(defn org-edit-save-cb [{:keys [success body status]}]
  (org-loaded (json->cljs body) true))

(defn org-edit-save [org-data]
  (api/patch-org org-data org-edit-save-cb))

(defn org-change [data org-data]
  (let [change-data (:data data)
        container-id (:container-id change-data)
        user-id (:user-id change-data)]
    (when (not= (jwt/user-id) user-id) ; no need to respond to our own events
      (when (= container-id (:uuid org-data))
        (utils/after 1000 get-org)))))

;; subscribe to websocket events
(defn subscribe []
  (ws-cc/subscribe :org/status
    (fn [data]
      (get-org)))
  (ws-cc/subscribe :container/change
    (fn [data]
      (let [change-data (:data data)
            change-type (:change-type change-data)
            org-data (dis/org-data)]
        ;; Handle section changes
        (org-change data org-data)
        ;; Nav away of the current section
        ;; if it's being deleted
        (when (and (= change-type :delete)
                   (= (:container-id change-data) (:uuid org-data)))
          (let [current-board-data (dis/board-data)]
            (when (= (:item-id change-data) (:uuid current-board-data))
              (router/nav! (oc-urls/all-posts (:slug org-data))))))))))