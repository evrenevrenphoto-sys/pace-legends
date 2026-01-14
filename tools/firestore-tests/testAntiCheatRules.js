const fs = require('fs');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');

const PROJECT_ID = 'pace-legends-test';

(async () => {
  const rules = fs.readFileSync('firestore.rules', 'utf8');
  const testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules }
  });

  try {
    console.log('✅ Test environment initialized');

    const now = Date.now();

    const alice = testEnv.authenticatedContext('aliceUid').firestore();
    const bob = testEnv.authenticatedContext('bobUid').firestore();
    const admin = testEnv.authenticatedContext('server', { admin: true }).firestore();

    // 1) Alice can create her own antiCheatLog (valid payload)
    await assertSucceeds(
      alice.collection('antiCheatLogs').doc('log1').set({
        userId: 'aliceUid',
        type: 'spike',
        violations: ['spike'],
        suspicionScore: 30,
        timestamp: now
      })
    );
    console.log('✔ Alice create own log - allowed');

    // 2) Bob cannot create a log for Alice (ownership enforced)
    await assertFails(
      bob.collection('antiCheatLogs').doc('log2').set({
        userId: 'aliceUid',
        type: 'spike',
        violations: ['spike'],
        suspicionScore: 30,
        timestamp: now
      })
    );
    console.log('✔ Bob cannot create log for Alice - blocked');

    // 3) Future timestamp should be rejected
    await assertFails(
      alice.collection('antiCheatLogs').doc('log3').set({
        userId: 'aliceUid',
        type: 'future',
        violations: ['timewarp'],
        suspicionScore: 10,
        timestamp: now + 1000 * 60 * 60 // +1 hour in future
      })
    );
    console.log('✔ Future timestamp rejected');

    // 4) Too many violations (>5) should be rejected
    const manyViolations = Array.from({ length: 6 }, (_, i) => `v${i}`);
    await assertFails(
      alice.collection('antiCheatLogs').doc('log4').set({
        userId: 'aliceUid',
        type: 'spam',
        violations: manyViolations,
        suspicionScore: 50,
        timestamp: now
      })
    );
    console.log('✔ Violation list size limit enforced');

    // 5) Rate limit: create meta with admin, then attempt to create log (should fail)
    await admin.doc('antiCheatMeta/aliceUid').set({ lastLogAt: now });
    await assertFails(
      alice.collection('antiCheatLogs').doc('log5').set({
        userId: 'aliceUid',
        type: 'spike',
        violations: ['spike'],
        suspicionScore: 20,
        timestamp: now
      })
    );
    console.log('✔ Rate limit enforced when meta.lastLogAt is recent');

    // 6) Admin read of logs should succeed
    await assertSucceeds(admin.collection('antiCheatLogs').doc('log1').get());
    console.log('✔ Admin can read logs');

    console.log('\nAll rule tests passed.');
  } catch (e) {
    console.error('Test failed:', e);
    process.exitCode = 1;
  } finally {
    await testEnv.cleanup();
  }
})();