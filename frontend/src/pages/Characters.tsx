import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { PlayerCharacter } from '../api/types';
import { Panel } from '../components/Panel';
import { ErrorBanner } from '../components/ErrorBanner';
import styles from './Characters.module.css';

/** specs/frontend/03-characters.md. */
export function Characters() {
  const queryClient = useQueryClient();
  const [characterName, setCharacterName] = useState('');

  const charactersQuery = useQuery({
    queryKey: ['characters'],
    queryFn: () => api.get<PlayerCharacter[]>('/characters'),
  });

  const addMutation = useMutation({
    mutationFn: () => api.post<PlayerCharacter>('/characters', { character_name: characterName }),
    onSuccess: () => {
      setCharacterName('');
      queryClient.invalidateQueries({ queryKey: ['characters'] });
    },
  });

  const removeMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/characters/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['characters'] }),
  });

  function handleAdd(e: FormEvent) {
    e.preventDefault();
    addMutation.mutate();
  }

  function handleRemove(id: number, name: string) {
    if (window.confirm(`Remove ${name}? Past runs stay in history, just unlinked from your account.`)) {
      removeMutation.mutate(id);
    }
  }

  return (
    <div>
      <h1>Characters</h1>
      <Panel>
        <ErrorBanner error={addMutation.error ?? removeMutation.error} />

        <form className={styles.form} onSubmit={handleAdd}>
          <label>
            Character name
            <input value={characterName} onChange={(e) => setCharacterName(e.target.value)} required />
          </label>
          <button type="submit" disabled={addMutation.isPending}>
            Add character
          </button>
        </form>

        {charactersQuery.isLoading && <p>Loading…</p>}
        {charactersQuery.data && (
          <table>
            <thead>
              <tr>
                <th>Character name</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {charactersQuery.data.map((character) => (
                <tr key={character.id}>
                  <td>{character.character_name}</td>
                  <td>
                    <button
                      onClick={() => handleRemove(character.id, character.character_name)}
                      disabled={removeMutation.isPending}
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}
