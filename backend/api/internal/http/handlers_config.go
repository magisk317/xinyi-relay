package http

// configCommandActor maps the authenticated principal to the (actorType,
// actorID) recorded in the device config audit log.
func configCommandActor(auth authContext) (string, int64) {
	switch auth.Kind {
	case authKindDevice:
		return "device", auth.Device.ID
	case authKindDesktop:
		return string(auth.Kind), auth.DesktopSession.ID
	case authKindSession:
		return "web_session", auth.User.ID
	default:
		return string(auth.Kind), auth.User.ID
	}
}
