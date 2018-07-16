(ns oc.web.actions.nux
  (:require-macros [if-let.core :refer (when-let*)])
  (:require [oc.web.lib.jwt :as jwt]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.actions.org :as org-actions]
            [oc.web.lib.json :refer (json->cljs cljs->json)]
            [oc.web.components.ui.slack-bot-modal :as slack-bot-modal]
            [oc.web.actions.notifications :as notification-actions]))

(declare dismiss-add-bot-notification)

(defn show-add-bot-notification [org-data team-data]
  ;; Do we have the needed data loaded
  (when-let [current-user-data (dis/current-user-data)]
    ;; Show the notification
    (notification-actions/show-notification {:title "Enable Carrot for Slack?"
                                             :description "Share post to Slack, sync comments, invite & manage your team."
                                             :id :slack-second-step-banner
                                             :dismiss dismiss-add-bot-notification
                                             :expire 0
                                             :slack-bot true
                                             :primary-bt-cb #(do
                                                               (dismiss-add-bot-notification)
                                                               (org-actions/bot-auth team-data current-user-data))
                                             :primary-bt-title [:span [:span.slack-icon] "Add to Slack"]
                                             :primary-bt-style :solid-green
                                             :primary-bt-dismiss true
                                             :secondary-bt-cb (fn [_]
                                                                (dismiss-add-bot-notification)
                                                                (slack-bot-modal/show-modal))
                                             :secondary-bt-title "Learn More"
                                             :secondary-bt-style :default-link
                                             :secondary-bt-dismiss true})))

(def new-user-cookie-value (atom nil))

(defn set-new-user-cookie
  "Create a map for the new user cookie and save it. Also update the value of
  the new-user-cookie-value atom."
  [user-type & [step]]
  (let [value-map {:user-type user-type
                   :step (or step 1)}
        json-map (cljs->json value-map)
        json-string (.stringify js/JSON json-map)]
    (reset! new-user-cookie-value value-map)
    (cook/set-cookie!
     (router/new-user-cookie (jwt/user-id))
      json-string
      (* 60 60 24 7))))

(defn get-new-user-cookie
  "Read the cookie from the document only if the new-user-cookie-value atom is nil.
  In all the other cases return the read value in the atom."
  []
  (when-not (map? @new-user-cookie-value)
    (if-let [string-json (cook/get-cookie (router/new-user-cookie (jwt/user-id)))]
      (reset! new-user-cookie-value (json->cljs string-json))
      (reset! new-user-cookie-value {})))
  @new-user-cookie-value)

(defn nux-end
  "NUX completed for the current user, remove the cookie and update the new-user-cookie-value."
  []
  (cook/remove-cookie! (router/new-user-cookie (jwt/user-id)))
  (reset! new-user-cookie-value {}))

(def default-first-post-user-id "1111-1111-1111")

(defn check-nux
  "NUX Logic:
  if user is new
    if has only one post and no dismiss add post TT cookie
      -> show add post TT
         on dismiss add dismiss add post TT cookie
          (recur)
    elif user is email and no dismiss invite user TT cookie
      -> show invite user TT
         on dismiss add dismiss invite user TT cookie, remove new user
          (recur)
    elif user is slack and no dismiss add bot NT
      -> show add bot notification
         on dismiss add dismiss add bot NT cookie, remove new user
          (recur)
    else
      -> show nux
         on dismiss remove new user cookie"
  []
  (when-let [{:keys [user-type step] :as new-user-cookie} (get-new-user-cookie)]
    (when-let* [org-data (dis/org-data)
                posts-data (dis/posts-data)]
      (let [team-data (dis/team-data (:team-id org-data))
            posts (vals (:fixed-items posts-data))
            first-post (first posts)
            first-post-author (when first-post
                                (if (map? (:author first-post))
                                   (:author first-post)
                                   (first (:author first-post))))
            filtered-boards (filterv #(not= (:slug %) utils/default-drafts-board-slug) (:boards org-data))
            should-show-first-tip (and ;; user has edit permission
                                       (utils/link-for (:links org-data) "create")
                                       ;; has only one board
                                       (= (count filtered-boards) 1)
                                       ;; or
                                       (or (and ;; has only one post
                                                (= (count posts) 1)
                                                first-post
                                                ;; from CarrotHQ
                                                (= (:user-id first-post-author) default-first-post-user-id))
                                            ;; has no posts
                                           (zero? (count (:fixed-items posts-data)))))
            can-proceed-to-second-step? (or ;; the user added a post
                                            (> (count posts) 1)
                                            ;; The user added a post but deleted our first
                                            (and (= (count posts) 1)
                                                 (not= (:user-id first-post-author) default-first-post-user-id)))
            should-show-second-tip (= (count (:users team-data)) 1) ;; user is alone in the team
            should-show-add-bot-notif (not (jwt/team-has-bot? (:team-id org-data)))
            iterate-next-step (fn []
                                (set-new-user-cookie user-type (inc step))
                                (check-nux))]
        (when (= step 1)
          (if should-show-first-tip
            (dis/dispatch! [:input [:show-add-post-tooltip] true])
            (dis/dispatch! [:input [:show-onboard-overlay] true])))
        (when (and (= step 2)
                   ;; Wait for the user to add at least a post
                   can-proceed-to-second-step?)
          ;; if there is no second step to apply
          (when (and (not should-show-add-bot-notif)
                     (not should-show-second-tip))
            ;; Iterate to the next directly
            (iterate-next-step))
          (if (= user-type "slack")
            (if should-show-add-bot-notif
              (show-add-bot-notification org-data team-data)
              (iterate-next-step))
            (if should-show-second-tip
              (dis/dispatch! [:input [:show-invite-people-tooltip] true])
              (iterate-next-step))))
        (when (= step 3)
          (nux-end))))))

(defn dismiss-onboard-overlay []
  (when-let [new-user-cookie (get-new-user-cookie)]
    (dis/dispatch! [:input [:show-onboard-overlay] nil])
    (set-new-user-cookie (:user-type new-user-cookie) 2)
    (check-nux)))

(defn dismiss-add-post-tooltip []
  (when-let [new-user-cookie (get-new-user-cookie)]
    (dis/dispatch! [:input [:show-add-post-tooltip] nil])
    (set-new-user-cookie (:user-type new-user-cookie) 2)
    (check-nux)))

(defn dismiss-invite-people-tooltip []
  (when-let [new-user-cookie (get-new-user-cookie)]
    (dis/dispatch! [:input [:show-invite-people-tooltip] nil])
    (set-new-user-cookie (:user-type new-user-cookie) 3)))

(defn dismiss-add-bot-notification []
  (when-let [new-user-cookie (get-new-user-cookie)]
    (set-new-user-cookie (:user-type new-user-cookie) 3)))