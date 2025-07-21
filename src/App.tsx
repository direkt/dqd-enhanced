import { Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import Dashboard from './pages/Dashboard'
import ProfileAnalysis from './pages/ProfileAnalysis'
import QueryAnalysis from './pages/QueryAnalysis'
import Reproduction from './pages/Reproduction'
import Settings from './pages/Settings'

function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/profile-analysis" element={<ProfileAnalysis />} />
        <Route path="/query-analysis" element={<QueryAnalysis />} />
        <Route path="/reproduction" element={<Reproduction />} />
        <Route path="/settings" element={<Settings />} />
      </Routes>
    </Layout>
  )
}

export default App 