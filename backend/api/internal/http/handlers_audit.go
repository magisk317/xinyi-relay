package http

import "net/http"

func (s *Server) handleConfigAuditLogs(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	limit := readLimitQuery(r, 50)
	offset := readOffsetQuery(r)
	logs, err := s.store.ListConfigAuditLogs(r.Context(), auth.User.ID, limit, offset)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "load config audit logs failed")
		return
	}

	items := make([]configAuditLogItem, 0, len(logs))
	for _, log := range logs {
		items = append(items, configAuditLogItem{
			ID:        log.ID,
			Revision:  log.Revision,
			ActorType: log.ActorType,
			ActorID:   log.ActorID,
			Summary:   log.Summary,
			CreatedAt: log.CreatedAt,
		})
	}

	writeJSON(w, http.StatusOK, configAuditLogsResponse{
		Logs:   items,
		Limit:  limit,
		Offset: offset,
	})
}
