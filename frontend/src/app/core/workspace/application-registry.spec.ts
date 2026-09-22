import { workspaceApplications } from './application-registry';

describe('workspaceApplications', () => {
  it('registers only the initial Developer OS applications', () => {
    expect(workspaceApplications).toEqual([
      { id: 'ORGANIZER', label: 'Organizer', destination: 'http://127.0.0.1:4200' },
      { id: 'DEVLOG', label: 'DevLog', destination: '/projects' },
    ]);
  });
});
