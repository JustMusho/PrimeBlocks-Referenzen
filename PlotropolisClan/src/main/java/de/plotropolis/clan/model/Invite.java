package de.plotropolis.clan.model;

import java.util.UUID;

public record Invite(String tag, UUID invitedBy, long createdAt) {}
