/**
 * Application/business logic — the seams the framework leaves blank.
 * LinkingService (proof on both ends, blocks account pre-hijacking),
 * VerificationStrategy (EMAIL real impl; kakao-msg/SMS/penny-drop stubs),
 * refresh-token reuse detection (CONSUMED re-presented -&gt; revoke whole family),
 * and the nullable-integrity guards for Credential STI gaps.
 */
package com.pigeonkim.oauth2authorizationserver.service;
