import { Link } from 'react-router-dom';
import { Panel } from '../components/Panel';
import styles from './HowToUse.module.css';

/** Static onboarding guide, /how-to-use. No API calls — just points at Account/Characters. */
export function HowToUse() {
  return (
    <div>
      <h1>How to Use</h1>

      <Panel className={styles.section}>
        <h2>First-time setup</h2>
        <p className={styles.intro}>Get the GW1 plugin uploading runs to your account.</p>

        <ol className={styles.steps}>
          <li className={styles.step}>
            <div>
              <p className={styles.stepTitle}>Set an alias (optional)</p>
              <p className={styles.stepBody}>
                On the <Link to="/account">Account</Link> page, set a display alias so other players can look you
                up instead of your raw username.
              </p>
            </div>
          </li>
          <li className={styles.step}>
            <div>
              <p className={styles.stepTitle}>Add your character(s)</p>
              <p className={styles.stepBody}>
                On the <Link to="/characters">Characters</Link> page, add each in-game character you play. The name
                must match your in-game character name exactly, including case — runs are matched to a character by
                exact name.
              </p>
            </div>
          </li>
          <li className={styles.step}>
            <div>
              <p className={styles.stepTitle}>Generate a machine key</p>
              <p className={styles.stepBody}>
                Back on the <Link to="/account">Account</Link> page, click "Generate new key". Copy it right away —
                it's shown once and can't be viewed again after you leave or dismiss it.
              </p>
            </div>
          </li>
          <li className={styles.step}>
            <div>
              <p className={styles.stepTitle}>Download SCTracker.dll</p>
              <p className={styles.stepBody}>
                Also on the <Link to="/account">Account</Link> page, download the plugin file.
              </p>
            </div>
          </li>
          <li className={styles.step}>
            <div>
              <p className={styles.stepTitle}>Put the plugin in your GWToolbox plugins folder</p>
              <p className={styles.stepBody}>
                Copy <code>SCTracker.dll</code> into your GWToolbox++ install's <code>Plugins</code> folder
                (create the folder if it doesn't already exist).
              </p>
            </div>
          </li>
          <li className={styles.step}>
            <div>
              <p className={styles.stepTitle}>Launch Guild Wars</p>
              <p className={styles.stepBody}>Start the game through GWToolbox as usual.</p>
            </div>
          </li>
          <li className={styles.step}>
            <div>
              <p className={styles.stepTitle}>Load the plugin</p>
              <p className={styles.stepBody}>Enable SCTracker from GWToolbox's plugin manager.</p>
            </div>
          </li>
          <li className={styles.step}>
            <div>
              <p className={styles.stepTitle}>Add your machine key</p>
              <p className={styles.stepBody}>
                Paste the machine key from step 3 into the plugin's settings and save. Runs upload automatically
                from then on.
              </p>
            </div>
          </li>
        </ol>

        <div className={styles.note}>
          Lost or revoked your key? Generate a new one from the <Link to="/account">Account</Link> page and update
          it in the plugin settings — the old key stops working as soon as it's revoked.
        </div>
      </Panel>

      <Panel className={styles.section}>
        <h2>Adding a new character later</h2>
        <p className={styles.intro}>Playing a new character? No need to touch your machine key.</p>

        <ol className={styles.steps}>
          <li className={styles.step}>
            <div>
              <p className={styles.stepTitle}>Go to Characters</p>
              <p className={styles.stepBody}>
                Open the <Link to="/characters">Characters</Link> page.
              </p>
            </div>
          </li>
          <li className={styles.step}>
            <div>
              <p className={styles.stepTitle}>Add the character</p>
              <p className={styles.stepBody}>
                Enter the character's exact in-game name (case-sensitive) and submit.
              </p>
            </div>
          </li>
          <li className={styles.step}>
            <div>
              <p className={styles.stepTitle}>That's it</p>
              <p className={styles.stepBody}>
                Your machine key is tied to your account, not any one character — runs uploaded for this character
                will link to your account automatically the next time you play it.
              </p>
            </div>
          </li>
        </ol>
      </Panel>
    </div>
  );
}
