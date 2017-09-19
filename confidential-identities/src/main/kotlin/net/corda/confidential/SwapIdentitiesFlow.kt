package net.corda.confidential

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.InitiatingFlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * Very basic flow which generates new confidential identities for parties in a transaction and exchanges the transaction
 * key and certificate paths between the parties. This is intended for use as a subflow of another flow which builds a
 * transaction.
 */
@StartableByRPC
@InitiatingFlow
class SwapIdentitiesFlow(private val otherParty: Party,
                         private val revocationEnabled: Boolean,
                         override val progressTracker: ProgressTracker) : InitiatingFlowLogic<LinkedHashMap<Party, AnonymousParty>>() {
    constructor(otherParty: Party) : this(otherParty, false, tracker())

    companion object {
        object AWAITING_KEY : ProgressTracker.Step("Awaiting key")

        fun tracker() = ProgressTracker(AWAITING_KEY)
        fun validateAndRegisterIdentity(identityService: IdentityService, otherSide: Party, anonymousOtherSide: PartyAndCertificate): PartyAndCertificate {
            require(anonymousOtherSide.name == otherSide.name)
            // Validate then store their identity so that we can prove the key in the transaction is owned by the
            // counterparty.
            identityService.verifyAndRegisterIdentity(anonymousOtherSide)
            return anonymousOtherSide
        }
    }

    @Suspendable
    override fun call(): LinkedHashMap<Party, AnonymousParty> {
        progressTracker.currentStep = AWAITING_KEY
        val legalIdentityAnonymous = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, revocationEnabled)

        // Special case that if we're both parties, a single identity is generated
        val identities = LinkedHashMap<Party, AnonymousParty>()
        if (serviceHub.myInfo.isLegalIdentity(otherParty)) {
            identities.put(otherParty, legalIdentityAnonymous.party.anonymise())
        } else {
            val otherSession = initiateFlow(otherParty)
            val anonymousOtherSide = otherSession.sendAndReceive<PartyAndCertificate>(legalIdentityAnonymous).unwrap { confidentialIdentity ->
                validateAndRegisterIdentity(serviceHub.identityService, otherSession.counterparty, confidentialIdentity)
            }
            identities.put(ourIdentity, legalIdentityAnonymous.party.anonymise())
            identities.put(otherSession.counterparty, anonymousOtherSide.party.anonymise())
        }
        return identities
    }

}
