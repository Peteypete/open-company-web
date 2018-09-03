(ns oc.web.actions.nux
  (:require-macros [if-let.core :refer (when-let*)])
  (:require [oc.web.lib.jwt :as jwt]
            [oc.web.router :as router]
            [oc.web.dispatcher :as dis]
            [oc.web.lib.utils :as utils]
            [oc.web.lib.cookies :as cook]
            [oc.web.actions.org :as org-actions]
            [oc.web.lib.json :refer (json->cljs cljs->json)]))

(defn get-nux-cookie
  "Read the cookie from the document only if the nux-cookie-value atom is nil.
  In all the other cases return the read value in the atom."
  []
  (json->cljs (cook/get-cookie (router/nux-cookie (jwt/user-id)))))

(defn set-nux-cookie
  "Create a map for the new user cookie and save it. Also update the value of
  the nux-cookie-value atom."
  [user-type value-map]
  (let [old-nux-cookie (get-nux-cookie)
        value-map (merge {:user-type user-type} old-nux-cookie value-map)
        json-map (cljs->json value-map)
        json-string (.stringify js/JSON json-map)]
    (cook/set-cookie!
     (router/nux-cookie (jwt/user-id))
      json-string
      (* 60 60 24 7))))

(defn new-user-registered [user-type]
  (set-nux-cookie user-type
    {:show-add-post-tooltip true
     :show-post-added-tooltip false
     :show-draft-post-tooltip true
     :show-edit-tooltip true
     :show-add-comment-tooltip true}))

(defn nux-end
  "NUX completed for the current user, remove the cookie and update the nux-cookie-value."
  []
  (cook/remove-cookie! (router/nux-cookie (jwt/user-id))))

;; Value used to save an already shown tooltip, use a string since keyword
;; will be lost during json read/write
(def default-tooltip-done "done")

(defn- parse-nux-cookie-value [v]
  (if (= v default-tooltip-done)
    false
    (boolean v)))

(defn- is-sample-entry? [entry-map]
  (let [published-at (:published-at entry-map)
        user-created-at (jwt/get-key :created-at)]
    (< published-at user-created-at)))

(defn mark-nux-step-done [nux-step-key]
  (when-let [nux-cookie (get-nux-cookie)]
    (set-nux-cookie (:user-type nux-cookie)
     {nux-step-key default-tooltip-done})))

(defn check-nux
  "NUX Logic:
   if user is new
     if "
  []
  (when-let* [nv (get-nux-cookie)
              org-data (dis/org-data)
              posts-data (dis/posts-data)
              team-data (dis/team-data)]
    (let [can-edit? (utils/is-admin-or-author? org-data)
          add-post-tooltip (:show-add-post-tooltip nv)
          post-added-tooltip (:show-post-added-tooltip nv)
          fixed-draft-post-tooltip (parse-nux-cookie-value (:show-draft-post-tooltip nv))
          edit-tooltip (:show-edit-tooltip nv)
          fixed-add-comment-tooltip (parse-nux-cookie-value (:show-add-comment-tooltip nv))
          user-type (:user-type nv)
          has-only-sample-posts (every? is-sample-entry? posts-data)
          team-has-more-users? (> (count (:users team-data)) 1)
          ;; Show add post tooltip if
          fixed-add-post-tooltip (and ;; it has not been done already
                                      (not= add-post-tooltip default-tooltip-done)
                                      ;; the user is not a viewer
                                      can-edit?
                                      ;; there are no added posts
                                      has-only-sample-posts
                                      ;; we are not showing the next tooltip (post added)
                                      (not post-added-tooltip))
          ;; Show the first post added tooltip
          fixed-post-added-tooltip (and ;; has not been done already
                                        (not= (:show-post-added-tooltip nv) default-tooltip-done)
                                        ;; team has only one user (self)
                                        team-has-more-users?)
          ;; Show the tooltip inside editing
          fixed-edit-tooltip (and ;; has not been done already
                                  (not= (:show-edit-tooltip nv) default-tooltip-done)
                                  ;; user is not a viewer
                                  can-edit?)]

      ;; If we don't need to show the first tooltip but it's
      ;; not marked as done let's mark it to remember
      (when (and (not fixed-add-post-tooltip)
                 (not= add-post-tooltip default-tooltip-done))
        (mark-nux-step-done :show-add-post-tooltip))
      (when (and (not fixed-post-added-tooltip)
                 (not= post-added-tooltip default-tooltip-done))
        (mark-nux-step-done :show-post-added-tooltip))
      (when (and (not fixed-edit-tooltip)
                 (not= edit-tooltip default-tooltip-done))
        (mark-nux-step-done :show-edit-tooltip))
      (dis/dispatch! [:input [:nux]
       {:show-add-post-tooltip fixed-add-post-tooltip
        :show-post-added-tooltip fixed-post-added-tooltip
        :show-edit-tooltip fixed-edit-tooltip
        :show-add-comment-tooltip fixed-add-comment-tooltip
        :show-draft-post-tooltip fixed-draft-post-tooltip
        :user-type user-type}]))))

(defn dismiss-add-post-tooltip []
  (mark-nux-step-done :show-add-post-tooltip)
  (check-nux))

(defn dismiss-add-comment-tooltip []
  (mark-nux-step-done :show-add-comment-tooltip)
  (check-nux))

(defn dismiss-post-added-tooltip []
  (mark-nux-step-done :show-post-added-tooltip)
  (check-nux))

(defn dismiss-edit-tooltip []
  (mark-nux-step-done :show-edit-tooltip)
  (check-nux))

(defn dismiss-draft-post-tooltip []
  (mark-nux-step-done :show-draft-post-tooltip)
  (check-nux))